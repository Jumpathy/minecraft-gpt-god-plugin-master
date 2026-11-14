package net.bigyous.gptgodmc.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.client.ModGuiMessages;
import net.bigyous.gptgodmc.client.ModGuiMessages.Command;
import net.bigyous.gptgodmc.client.ModGuiMessages.CostGod;
import net.bigyous.gptgodmc.client.ModGuiMessages.CostView;
import net.bigyous.gptgodmc.client.ModGuiMessages.Dial;
import net.bigyous.gptgodmc.client.ModGuiMessages.Entry;
import net.bigyous.gptgodmc.client.ModGuiMessages.GodDetail;
import net.bigyous.gptgodmc.client.ModGuiMessages.GodRow;
import net.bigyous.gptgodmc.client.ModGuiMessages.JournalLine;
import net.bigyous.gptgodmc.client.ModGuiMessages.MortalDetail;
import net.bigyous.gptgodmc.client.ModGuiMessages.MortalRow;
import net.bigyous.gptgodmc.client.ModGuiMessages.Named;
import net.bigyous.gptgodmc.client.ModGuiMessages.Report;
import net.bigyous.gptgodmc.client.ModGuiRequests;
import net.bigyous.gptgodmc.client.ModGuiRequests.Confirm;
import net.bigyous.gptgodmc.client.ModGuiRequests.Request;
import net.bigyous.gptgodmc.client.ModGuiRequests.Spec;
import net.bigyous.gptgodmc.client.ModMessages;
import net.bigyous.gptgodmc.client.ModSessions;
import net.bigyous.gptgodmc.god.vessel.VesselHeads;

/**
 * The God Manager screen's protocol. The golden files in client-mod/src/test/resources/protocol are what
 * the client mod's tests decode (gui-*.json) and write (req-*.json); the mod also ships the gui-*.json
 * files as its sample data for previews. Run the tests with the environment variable GUI_FIXTURES_WRITE=1 to write them anew.
 */
class ModGuiTest {
    private static final Path DIR = Path.of("client-mod/src/test/resources/protocol");
    private static final boolean WRITE = Boolean.getBoolean("gui.fixtures.write") || System.getenv("GUI_FIXTURES_WRITE") != null;
    private static final long T = 1_700_000_000_000L;

    // ---- Sample messages (the golden files) ------------------------------------------------------

    static GodRow vaelor() {
        return new GodRow("main", "Vaelor", "the Ember Judge", "wrathful", 0xFF5A30, "supreme", "awake", true,
                "wrathful", new double[] { 64, 5, 12, 20, 31 }, "walks", 2, 62.0, 100.0, "infernal", false);
    }

    static List<GodRow> gods() {
        return List.of(vaelor(),
                new GodRow("ysmere", "Ysmere", "Keeper of Green Hollows", "benevolent", 0x7CE08A, "greater", "paused",
                        false, "joyful", new double[] { 0, 58, 6, 0, 22 }, "formless", 0, null, null, "grove", true),
                new GodRow("god3", null, null, "trickster", 0x9BE564, "lesser", "asleep", false, "calm",
                        new double[] { 0, 0, 0, 0, 0 }, "formless", 0, null, null, "jester", true),
                new GodRow("morth", "Morthuun", "the Hollow Tide", "cosmic", 0x8A8A90, "greater", "slain", false, "calm",
                        new double[] { 10, 0, 40, 66, 0 }, "formless", 0, 0.0, 100.0, "abyss", true));
    }

    static Map<String, JsonObject> golden() {
        Map<String, JsonObject> m = new LinkedHashMap<>();
        m.put("gui-open", ModGuiMessages.open("main", true, true, true));
        m.put("gui-result-ok", ModGuiMessages.result("7", true, null, null,
                "Command #5 delivered: the god must obey. You will hear when it is done.", null));
        m.put("gui-result-field", ModGuiMessages.result("8", false, "name",
                "\"Zeus\" is the name of a famous god; choose an invented one.", null, null));
        m.put("gui-result-needs", ModGuiMessages.result("9", false, null, "Type its name (Vaelor) to confirm.", null,
                "name"));
        m.put("gui-note", ModGuiMessages.note("Born: Ysmere, Keeper of Green Hollows (benevolent). Its memory starts "
                + "fresh.", "info"));
        m.put("gui-pantheon", ModGuiMessages.pantheon(gods()));
        m.put("gui-god", ModGuiMessages.god(new GodDetail(vaelor(), true, true, false, false,
                List.of("strong wrath (64)", "some glory (31)"), "claude-opus-5-5", null, "claude-sonnet-5-5", "auto",
                "auto", "bm_george", "auto", "kokoro", true, "smite", true, true,
                List.of(new Dial("generosity", 3, 4), new Dial("wrath", 10, 9), new Dial("chattiness", 4, 4),
                        new Dial("humor", 0, 1), new Dial("meddling", 6, 6), new Dial("patience", 2, 2),
                        new Dial("mercy", 2, 2), new Dial("pride", 9, 9), new Dial("curiosity", 5, 3),
                        new Dial("vanity", 8, 8)),
                10, List.of("Moved by: joy ← the sky cleared (3m ago); wrath ← Korvath insulted you (12m ago)",
                        "Sky: a waning crescent moon; the rain stopped 4 min ago.",
                        "Memory (8m ago): Ashwyn is here now. You remember: Ashwyn built a shrine of gold near "
                                + "spawn (12d ago)."))));
        m.put("gui-commands", ModGuiMessages.commands("main",
                List.of(new Command(12, "Build a temple of blackstone on the hill east of spawn, then speak of it to "
                        + "everyone.", false, "Seen in 1 of 3 thoughts", "Tyler (God Manager)"),
                        new Command(13, "For the next 10 minutes you are WRATHFUL: your patience is spent. Let fury "
                                + "colour every word and act.", true, "8 min left", "Tyler (God Manager)")),
                List.of(new Report(801, T - 420_000, "Command #10 done: I blessed all four mortals with haste and "
                        + "told them why."),
                        new Report(822, T - 180_000, "Command #11 impossible: no mortal has slain another; there is "
                                + "no killer to punish."))));
        m.put("gui-mortals", ModGuiMessages.mortals("main", "", 0, 1, 6,
                List.of(new MortalRow("Tyler", 9, "chosen", true, "Flamebearer", null, 4, 12),
                        new MortalRow("Korvath", -8, "despised", true, "the Faithless", null, 3, 9),
                        new MortalRow("Mirelle", 4, "favored", true, null, null, 1, 5),
                        new MortalRow("Dunmar", 0, "unproven", false, null, "3h", 0, 2),
                        new MortalRow("Ashka", -4, "out of favor", false, null, "2d", 2, 6),
                        new MortalRow("Pip_the_Small", 2, "unproven", false, "Keeper of Bees", "5d", 1, 1))));
        m.put("gui-mortal", ModGuiMessages.mortal("main", new MortalDetail("Korvath", -8, "despised", true,
                "the Faithless", "He came humbly at first, built a chapel, then burned it when a prayer for diamonds "
                        + "went unanswered. Since then he mocks every rite and dares the god to strike him.",
                2, null,
                List.of(new Entry(T - 600_000, "He mocked you before the others; never forgive him.", "10m"),
                        new Entry(T - 7_200_000, "Burned the chapel at the crossroads.", "2h"),
                        new Entry(T - 90_000_000, "Asked for diamonds three times in one hour.", "1d")),
                List.of(new Entry(T - 300_000, "Insulted the god in chat: \"some god you are\".", "5m"),
                        new Entry(T - 3_600_000, "Killed Mirelle near the river.", "1h"),
                        new Entry(T - 7_200_000, "Set fire to the chapel.", "2h"),
                        new Entry(T - 86_400_000, "Offered rotten flesh at the vessel's feet.", "1d")))));
        m.put("gui-mortal-none", ModGuiMessages.mortal("main", null));
        m.put("gui-journal", ModGuiMessages.journal("main", List.of(
                new JournalLine(830, T - 95_000, "info", "Binding command #12 from Tyler (God Manager): Build a temple "
                        + "of blackstone on the hill east of spawn, then speak of it to everyone."),
                new JournalLine(831, T - 90_000, "thought", "Tyler commands a temple. The eastern hill is bare; "
                        + "blackstone suits my temper today. Korvath is watching: let him see what devotion earns."),
                new JournalLine(832, T - 84_000, "tool", "build(design=temple, material=polished_blackstone, at=hill "
                        + "east of spawn) → building 1,840 blocks"),
                new JournalLine(833, T - 80_000, "speech", "announce: \"Mortals. Look east, and learn what is owed to "
                        + "me.\""),
                new JournalLine(834, T - 61_000, "tool", "smite(player=Korvath) → lightning struck beside Korvath (no "
                        + "damage)"),
                new JournalLine(835, T - 60_000, "error", "vessel_go: no path to the hill; flying instead."),
                new JournalLine(836, T - 31_000, "thought", "The walls rise. I will finish the roof before I answer "
                        + "Mirelle's prayer; she can wait a moment longer."),
                new JournalLine(837, T - 12_000, "cycle", "Thought #212 ended after 4 steps.")), true));
        m.put("gui-switches", ModGuiMessages.switches(List.of(
                new ModGuiMessages.Switch("vessel.enabled", "The god's body", "Whether the god can take a physical "
                        + "vessel at all.", true),
                new ModGuiMessages.Switch("powers.commands", "Server commands", "The god may run server commands "
                        + "(minus the blocklist).", false))));
        double[] spark = new double[60];
        for (int i = 0; i < 60; i++) spark[i] = i % 7 == 0 ? 0 : Math.round((0.004 + 0.003 * Math.sin(i * 0.7) + (i > 50 ? 0.006 : 0)) * 10000) / 10000.0;
        m.put("gui-costs", ModGuiMessages.costs(new CostView(0.0131, 0.412, 3.9052, 1.2745, 48.3391, true, 318,
                412_300, 96_450, 9_804_112, 233_870,
                List.of(new CostGod("main", "Vaelor", 0.301, 2.84, 0.93), new CostGod("ysmere", "Ysmere", 0.111, 1.0652,
                        0.3445), new CostGod("god3", null, 0, 0, 0)),
                spark)));
        m.put("gui-costs-off", ModGuiMessages.costs(null));
        m.put("gui-config", ModGuiMessages.config("Sample", List.of(
                new ModGuiMessages.Option("brain.enabled", "Thinking", "bool", 0, 1, 1, List.of(), "true", "",
                        "Off: it never calls Claude.", false, false, "true"),
                new ModGuiMessages.Option("brain.heartbeat-seconds", "Heartbeat", "int", 5, 600, 5, List.of(), "40",
                        "s", "With events waiting, it looks at the world this often.", false, false, "25"),
                new ModGuiMessages.Option("voice.tts.volume", "Volume", "double", 0.1, 2.0, 0.1, List.of(), "1.0",
                        "x", "How loud its voice is.", false, false, "1.3"),
                new ModGuiMessages.Option("initiative.level", "Free will", "choice", 0, 0, 1,
                        List.of("watch", "semi", "free"), "semi", "", "Watch, semi or free.", false, false, "free"),
                new ModGuiMessages.Option("viewer.port", "Mind viewer port", "int", 1024, 65535, 1, List.of(), "4673",
                        "", "The port of the web page that shows its mind.", true, true, "4673"))));
        return m;
    }

    /** Requests the mod writes; the plugin must parse exactly these. */
    static final Map<String, String> REQUESTS = Map.of(
            "req-open", "{\"t\":\"gui_open\",\"id\":\"1\"}",
            "req-sub", "{\"t\":\"gui_sub\",\"id\":\"2\",\"realm\":\"main\",\"part\":\"mortals\",\"args\":{\"query\":\"kor\",\"page\":\"0\",\"player\":\"Korvath\"}}",
            "req-act", "{\"t\":\"gui_act\",\"id\":\"3\",\"realm\":\"main\",\"action\":\"command\",\"args\":{\"text\":\"Build a temple on the hill.\"}}",
            "req-act-confirm", "{\"t\":\"gui_act\",\"id\":\"4\",\"realm\":\"main\",\"action\":\"rebirth\",\"args\":{\"archetype\":\"chill\"},\"confirm\":\"Vaelor\"}",
            "req-close", "{\"t\":\"gui_close\"}");

    /** Samples for previews, written with the flag but not compared (they follow the menu's own lists). */
    static Map<String, JsonObject> samples() {
        Map<String, JsonObject> m = new LinkedHashMap<>();
        for (String tab : Advanced.TABS) {
            List<ModGuiMessages.Option> options = new ArrayList<>();
            for (Advanced.Option o : Advanced.of(tab)) options.add(ModGuiViews.option(o, o.fallback()));
            m.put("gui-sample-config-" + tab, ModGuiMessages.config(tab, options));
        }
        m.put("gui-sample-catalog", ModGuiMessages.catalog(ModGuiViews.catalog(List.of(
                new Named("benevolent", "A warm guardian deity who delights in mortals.", 0xFFD966),
                new Named("chill", "A laid-back, easygoing god.", 0x7FD4FF),
                new Named("trickster", "A mischievous chaos god who loves pranks.", 0x9BE564),
                new Named("evil", "A malevolent dark god who sees mortals as playthings.", 0xD0303A),
                new Named("wrathful", "A stern, jealous old god of law and judgment.", 0xFF7A30),
                new Named("cosmic", "An ancient, alien intelligence from beyond the stars.", 0xB070FF)))));
        List<ModGuiMessages.Switch> switches = new ArrayList<>();
        for (MenuModel.Setting set : MenuLayout.SETTINGS) {
            switches.add(new ModGuiMessages.Switch(set.key(), set.label(), set.description(),
                    set.fallback() && !set.key().equals("powers.commands")));
        }
        m.put("gui-sample-switches", ModGuiMessages.switches(switches));
        // Every look's head, in two messages (a dozen each, as the plugin sends them).
        Map<String, byte[]> heads = new LinkedHashMap<>();
        int part = 1;
        for (String look : net.bigyous.gptgodmc.god.vessel.VesselLook.NAMES) {
            heads.put(look, VesselHeads.head(look));
            if (heads.size() == 13) {
                m.put(part == 1 ? "gui-sample-heads" : "gui-sample-heads-" + part, ModGuiMessages.heads(heads));
                heads = new LinkedHashMap<>();
                part++;
            }
        }
        if (!heads.isEmpty()) m.put(part == 1 ? "gui-sample-heads" : "gui-sample-heads-" + part, ModGuiMessages.heads(heads));
        return m;
    }

    @Test
    void messagesMatchGolden() throws Exception {
        var gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        Map<String, JsonObject> all = new LinkedHashMap<>(golden());
        if (WRITE) {
            all.putAll(samples());
            for (var e : all.entrySet()) {
                Files.writeString(DIR.resolve(e.getKey() + ".json"), gson.toJson(e.getValue()) + "\n");
            }
            for (var e : REQUESTS.entrySet()) Files.writeString(DIR.resolve(e.getKey() + ".json"), e.getValue() + "\n");
        }
        for (var e : golden().entrySet()) {
            Path file = DIR.resolve(e.getKey() + ".json");
            assertTrue(Files.exists(file), "missing golden file " + file + " (run with GUI_FIXTURES_WRITE=1)");
            assertEquals(JsonParser.parseString(Files.readString(file)),
                    JsonParser.parseString(ModMessages.json(e.getValue())), e.getKey());
        }
    }

    @Test
    void everyMessageFitsTheSizeCap() {
        Map<String, JsonObject> all = new LinkedHashMap<>(golden());
        all.putAll(samples());
        for (var e : all.entrySet()) {
            int bytes = ModMessages.bytes(e.getValue()).length;
            assertTrue(bytes <= ModSessions.MAX_BYTES, e.getKey() + " is " + bytes + " bytes");
        }
    }

    @Test
    void longTextsAreClipped() {
        String huge = "x".repeat(50_000);
        List<JournalLine> lines = new ArrayList<>();
        for (int i = 0; i < 30; i++) lines.add(new JournalLine(i, T, "thought", huge));
        assertTrue(ModMessages.bytes(ModGuiMessages.journal("main", lines, false)).length <= ModSessions.MAX_BYTES);
        List<Entry> notes = new ArrayList<>();
        for (int i = 0; i < 500; i++) notes.add(new Entry(i, huge, "1d"));
        JsonObject mortal = ModGuiMessages.mortal("main", new MortalDetail("A", 0, "unproven", false, huge, huge, 0,
                null, notes, notes));
        assertTrue(ModMessages.bytes(mortal).length <= ModSessions.MAX_BYTES);
        assertEquals(500, mortal.get("notes_total").getAsInt());
        assertEquals(40, mortal.getAsJsonArray("notes").size());
    }

    // ---- Requests ---------------------------------------------------------------------------------

    @Test
    void theModsRequestsParse() throws Exception {
        for (String name : REQUESTS.keySet()) {
            Path file = DIR.resolve(name + ".json");
            assertTrue(Files.exists(file), "missing " + file);
            assertNotNull(ModGuiRequests.parse(Files.readAllBytes(file)), name);
        }
        Request sub = ModGuiRequests.parse(REQUESTS.get("req-sub").getBytes(StandardCharsets.UTF_8));
        assertEquals("mortals", sub.part());
        assertEquals("Korvath", sub.arg("player"));
        Request act = ModGuiRequests.parse(REQUESTS.get("req-act-confirm").getBytes(StandardCharsets.UTF_8));
        assertEquals("rebirth", act.action());
        assertEquals("Vaelor", act.confirm());
        assertEquals("4", act.id());
    }

    @Test
    void hostileRequestsAreRefused() {
        for (String bad : List.of("", "not json", "[]", "{}", "{\"t\":\"god\"}", "{\"t\":\"gui_act\"}",
                "{\"t\":\"gui_act\",\"action\":\"op_me\"}", "{\"t\":\"gui_sub\",\"part\":\"prompts\"}",
                "{\"t\":\"gui_sub\"}", "{\"t\":5}")) {
            assertNull(ModGuiRequests.parse(bad.getBytes(StandardCharsets.UTF_8)), bad);
        }
        String big = "{\"t\":\"gui_act\",\"action\":\"command\",\"args\":{\"text\":\"" + "a".repeat(9000) + "\"}}";
        assertNull(ModGuiRequests.parse(big.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void stringsAreClippedAndCleaned() {
        String json = "{\"t\":\"gui_act\",\"id\":\"" + "9".repeat(100) + "\",\"action\":\"command\",\"args\":{\"text\":\""
                + "§cred\\nline\\u0000 " + "b".repeat(3000) + "\",\"a\":1,\"b\":2,\"c\":3,\"d\":4,\"e\":5,\"f\":6,\"g\":7,"
                + "\"h\":8,\"i\":9,\"nested\":{\"x\":1}}}";
        Request r = ModGuiRequests.parse(json.getBytes(StandardCharsets.UTF_8));
        assertNotNull(r);
        assertEquals(24, r.id().length());
        assertTrue(r.text("text").startsWith("cred line"));
        assertTrue(r.text("text").length() <= 1200);
        assertTrue(r.args().size() <= 8);
        assertNull(r.arg("nested"));
    }

    @Test
    void missingArgumentsAreNamed() {
        Request r = ModGuiRequests.parse("{\"t\":\"gui_act\",\"action\":\"command\",\"args\":{\"text\":\"  \"}}"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals("text", ModGuiRequests.missing(r));
        Request ok = ModGuiRequests.parse("{\"t\":\"gui_act\",\"action\":\"wake\"}".getBytes(StandardCharsets.UTF_8));
        assertNull(ModGuiRequests.missing(ok));
    }

    @Test
    void clientMessagesAreRateLimited() {
        ModGuiRequests.Limiter l = new ModGuiRequests.Limiter();
        UUID id = UUID.randomUUID();
        int through = 0;
        for (int i = 0; i < 500; i++) if (l.allow(id, 1000)) through++;
        assertEquals((int) ModGuiRequests.RATE, through);
        assertTrue(l.allow(id, 1100));
    }

    @Test
    void everyActionIsAChestMenuClick() {
        // The two exceptions are edits the mind viewer has too; everything else is carried out by the
        // chest menu's own click, so the screen cannot do anything the menu cannot.
        for (Map.Entry<String, Spec> e : ModGuiRequests.ACTIONS.entrySet()) {
            if (List.of("title_set", "summary_set", "revive").contains(e.getKey())) continue;
            assertNotNull(e.getValue().kind(), e.getKey());
        }
    }

    @Test
    void dangerousActionsNeedConfirming() {
        Map<String, Spec> a = ModGuiRequests.ACTIONS;
        for (String name : List.of("rebirth", "remove_god")) {
            assertEquals(Confirm.NAME, a.get(name).confirm(), name);
            assertEquals("name", ModGuiRequests.needs(a.get(name), null, "Vaelor"));
            assertEquals("name", ModGuiRequests.needs(a.get(name), "true", "Vaelor"));
            assertEquals("name", ModGuiRequests.needs(a.get(name), "Vaelo", "Vaelor"));
            assertNull(ModGuiRequests.needs(a.get(name), " vaelor ", "Vaelor"));
        }
        for (String name : List.of("forget", "notes_clear", "deeds_clear", "true_form")) {
            assertEquals("confirm", ModGuiRequests.needs(a.get(name), null, "Vaelor"), name);
            assertNull(ModGuiRequests.needs(a.get(name), "true", "Vaelor"), name);
        }
        assertNull(ModGuiRequests.needs(a.get("wake"), null, "Vaelor"));
    }

    // ---- Config values, costs, heads --------------------------------------------------------------

    @Test
    void configValuesKeepTheMenusBounds() {
        Advanced.Option beat = Advanced.find("brain.heartbeat-seconds");
        assertNull(ModGuiActions.valueProblem(beat, "40"));
        assertNotNull(ModGuiActions.valueProblem(beat, "4"));
        assertNotNull(ModGuiActions.valueProblem(beat, "601"));
        assertNotNull(ModGuiActions.valueProblem(beat, "12.5"));
        assertNotNull(ModGuiActions.valueProblem(beat, "soon"));
        assertNotNull(ModGuiActions.valueProblem(beat, "NaN"));
        assertTrue(ModGuiActions.valueProblem(beat, "9999").contains("between 5 and 600"));
        Advanced.Option volume = Advanced.find("voice.tts.volume");
        assertNull(ModGuiActions.valueProblem(volume, "1.25"));
        assertEquals("1.25", ModGuiActions.normal(volume, "1.2500"));
        assertNotNull(ModGuiActions.valueProblem(volume, "3"));
        Advanced.Option free = Advanced.find("initiative.level");
        assertNull(ModGuiActions.valueProblem(free, "free"));
        assertNotNull(ModGuiActions.valueProblem(free, "wild"));
        Advanced.Option on = Advanced.find("brain.enabled");
        assertNull(ModGuiActions.valueProblem(on, "false"));
        assertNotNull(ModGuiActions.valueProblem(on, "maybe"));
        for (Advanced.Option o : Advanced.OPTIONS) {
            assertNull(ModGuiActions.valueProblem(o, o.fallback()), o.key() + " refuses its own default");
        }
    }

    @Test
    void portsAndRestartSettingsAreReadOnly() {
        assertTrue(ModGuiViews.locked(Advanced.find("viewer.port")));
        assertTrue(ModGuiViews.locked(Advanced.find("bridge.enabled")));
        assertTrue(ModGuiViews.locked(Advanced.find("rounds.enabled")));
        assertFalse(ModGuiViews.locked(Advanced.find("brain.enabled")));
    }

    @Test
    void headsAreSmallAndStable() {
        byte[] a = VesselHeads.head("infernal");
        assertEquals(16 * 16 * 3, a.length);
        assertArrayEquals(a, VesselHeads.head("infernal"));
        assertFalse(java.util.Arrays.equals(a, VesselHeads.head("radiant")));
        assertArrayEquals(VesselHeads.head("shade"), VesselHeads.head("no-such-look"));
    }

    @Test
    void commandReportsAreFoundInTheJournal() {
        assertTrue(ModGuiViews.isReport("Command #4 done: the temple stands."));
        assertTrue(ModGuiViews.isReport("Command #4 cancelled by the operator"));
        assertFalse(ModGuiViews.isReport("Binding command #4 from Tyler: build"));
    }
}
