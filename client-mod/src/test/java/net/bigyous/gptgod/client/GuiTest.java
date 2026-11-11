package net.bigyous.gptgod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.bigyous.gptgod.client.gui.GuiModel;
import net.bigyous.gptgod.client.gui.GuiProtocol;

/**
 * The God Manager screen's pure parts: decoding the plugin's golden messages (the same files the plugin's
 * tests check it writes), writing requests the plugin's tests parse, and the screen's own state:
 * going back, answers landing beside the right field, command history, journal and config filters.
 */
class GuiTest {
    private static final Path DIR = Path.of("src/test/resources/protocol");

    private static byte[] golden(String name) throws Exception {
        return Files.readAllBytes(DIR.resolve(name + ".json"));
    }

    private static <T> T decode(Class<T> type, String name) throws Exception {
        return assertInstanceOf(type, GuiProtocol.decode(golden(name)), name);
    }

    // ---- Decoding ---------------------------------------------------------------------------------

    @Test
    void openResultAndNoteDecode() throws Exception {
        GuiProtocol.Open o = decode(GuiProtocol.Open.class, "gui-open");
        assertEquals("main", o.realm());
        assertTrue(o.several() && o.costs() && o.pantheon());
        GuiProtocol.Result ok = decode(GuiProtocol.Result.class, "gui-result-ok");
        assertTrue(ok.ok());
        assertTrue(ok.message().startsWith("Command #5 delivered"));
        GuiProtocol.Result field = decode(GuiProtocol.Result.class, "gui-result-field");
        assertFalse(field.ok());
        assertEquals("name", field.field());
        assertTrue(field.error().contains("Zeus"));
        assertEquals("name", decode(GuiProtocol.Result.class, "gui-result-needs").needs());
        assertTrue(decode(GuiProtocol.Note.class, "gui-note").text().startsWith("Born: Ysmere"));
    }

    @Test
    void pantheonAndGodDecode() throws Exception {
        GuiProtocol.Pantheon p = decode(GuiProtocol.Pantheon.class, "gui-pantheon");
        assertEquals(4, p.gods().size());
        GuiProtocol.God v = p.gods().get(0);
        assertEquals("Vaelor", v.name());
        assertEquals(0xFF5A30, v.color());
        assertTrue(v.thinking());
        assertEquals(64f, v.heart()[0]);
        assertTrue(v.wounded());
        assertEquals("infernal", v.look());
        assertEquals("Unborn god", p.gods().get(2).label());
        assertFalse(p.gods().get(1).wounded());
        assertTrue(p.gods().get(1).removable());
        GuiProtocol.GodDetail d = decode(GuiProtocol.GodDetail.class, "gui-god");
        assertEquals("main", d.realm());
        assertEquals(10, d.dials().size());
        assertEquals("claude-opus-5-5", d.model());
        assertNull(d.effort());
        assertEquals("smite", d.gaze());
        assertTrue(d.born() && d.awake() && !d.paused());
    }

    @Test
    void listsDecode() throws Exception {
        GuiProtocol.Commands c = decode(GuiProtocol.Commands.class, "gui-commands");
        assertEquals(2, c.pending().size());
        assertTrue(c.pending().get(1).standing());
        assertEquals(2, c.reports().size());
        GuiProtocol.Mortals ms = decode(GuiProtocol.Mortals.class, "gui-mortals");
        assertEquals(6, ms.rows().size());
        assertEquals(-8, ms.rows().get(1).favor());
        GuiProtocol.Mortal one = decode(GuiProtocol.Mortal.class, "gui-mortal");
        assertEquals("Korvath", one.name());
        assertEquals(3, one.notes().size());
        assertEquals(4, one.deedsTotal());
        assertNull(decode(GuiProtocol.Mortal.class, "gui-mortal-none").name());
        GuiProtocol.Journal j = decode(GuiProtocol.Journal.class, "gui-journal");
        assertTrue(j.reset());
        assertEquals(8, j.lines().size());
        GuiProtocol.Config cfg = decode(GuiProtocol.Config.class, "gui-config");
        assertEquals(5, cfg.options().size());
        assertTrue(cfg.options().get(4).locked());
        assertEquals(600, cfg.options().get(1).max());
        assertEquals(List.of("watch", "semi", "free"), cfg.options().get(3).choices());
        assertEquals(2, decode(GuiProtocol.Switches.class, "gui-switches").list().size());
        GuiProtocol.Costs costs = decode(GuiProtocol.Costs.class, "gui-costs");
        assertEquals(0.412, costs.hour(), 1e-9);
        assertEquals(48.3391, costs.total(), 1e-9);
        assertEquals(60, costs.spark().length);
        assertEquals(3, costs.gods().size());
        assertTrue(decode(GuiProtocol.Costs.class, "gui-costs-off").off());
    }

    @Test
    void everySampleShippedInTheJarDecodes() throws Exception {
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path f : files.filter(p -> p.getFileName().toString().startsWith("gui-")).toList()) {
                assertNotNull(GuiProtocol.decode(Files.readAllBytes(f)), f.toString());
            }
        }
        GuiProtocol.Heads heads = decode(GuiProtocol.Heads.class, "gui-sample-heads");
        assertEquals(16, heads.size());
        assertEquals(256, heads.pixels().get("infernal").length);
        assertEquals(0xFF000000, heads.pixels().get("infernal")[0] & 0xFF000000);
        GuiProtocol.Catalog cat = decode(GuiProtocol.Catalog.class, "gui-sample-catalog");
        assertFalse(cat.looks().isEmpty());
        assertEquals("auto", cat.looks().get(0).key());
        assertEquals(8, cat.tabs().size());
        assertEquals(6, cat.presets().size());
    }

    @Test
    void hostileMessagesNeverThrow() {
        for (String bad : List.of("", "x", "[]", "{}", "{\"t\":\"gui_data\"}", "{\"t\":\"gui_data\",\"part\":\"god\"}",
                "{\"t\":\"gui_data\",\"part\":\"pantheon\",\"gods\":[1,\"x\",{\"heart\":7,\"color\":\"red\"}]}",
                "{\"t\":\"gui_data\",\"part\":\"heads\",\"size\":99999,\"heads\":{\"a\":\"!!\",\"b\":5}}",
                "{\"t\":\"gui_data\",\"part\":\"costs\",\"spark\":\"no\",\"hour\":\"NaN\"}",
                "{\"t\":\"gui_data\",\"part\":\"config\",\"options\":[{\"type\":\"wat\"},{\"type\":\"int\",\"step\":0}]}",
                "{\"t\":\"gui_result\",\"id\":{}}")) {
            GuiProtocol.decode(bad.getBytes(StandardCharsets.UTF_8)); // must not throw
        }
        Object p = GuiProtocol.decode("{\"t\":\"gui_data\",\"part\":\"pantheon\",\"gods\":[{\"name\":\"§cEvil\\u0000\",\"heart\":[999,-5]}]}"
                .getBytes(StandardCharsets.UTF_8));
        GuiProtocol.God g = assertInstanceOf(GuiProtocol.Pantheon.class, p).gods().get(0);
        assertEquals("cEvil", g.name());
        assertEquals(100f, g.heart()[0]);
        assertEquals(0f, g.heart()[1]);
    }

    // ---- Requests ---------------------------------------------------------------------------------

    private static void same(String name, byte[] written) throws Exception {
        assertEquals(JsonParser.parseString(new String(golden(name), StandardCharsets.UTF_8)),
                JsonParser.parseString(new String(written, StandardCharsets.UTF_8)), name);
    }

    @Test
    void requestsMatchGolden() throws Exception {
        same("req-open", GuiProtocol.open("1"));
        Map<String, String> args = new LinkedHashMap<>();
        args.put("query", "kor");
        args.put("page", "0");
        args.put("player", "Korvath");
        same("req-sub", GuiProtocol.sub("2", "main", "mortals", args));
        same("req-act", GuiProtocol.act("3", "main", "command", Map.of("text", "Build a temple on the hill."), null));
        same("req-act-confirm", GuiProtocol.act("4", "main", "rebirth", Map.of("archetype", "chill"), "Vaelor"));
        same("req-close", GuiProtocol.close());
    }

    @Test
    void oversizedArgumentsAreCutBeforeSending() {
        byte[] b = GuiProtocol.act("1", "main", "command", Map.of("text", "x".repeat(50_000)), null);
        assertTrue(b.length < 8 * 1024);
    }

    // ---- The screen's state -----------------------------------------------------------------------

    private static GuiModel opened(boolean several) {
        GuiModel m = new GuiModel();
        m.accept(new GuiProtocol.Open("main", several, true, true), 0);
        return m;
    }

    @Test
    void opensOnTheGodOrThePantheon() {
        assertEquals(new GuiModel.Route(GuiModel.OVERVIEW, "main"), opened(false).route());
        assertEquals(new GuiModel.Route(GuiModel.PANTHEON, null), opened(true).route());
    }

    @Test
    void backReturnsToThePreviousPageAndClosesOnlyFromTheFirst() {
        GuiModel m = opened(true);
        assertFalse(m.canGoBack());
        m.go(GuiModel.OVERVIEW, "main");
        m.tab(GuiModel.COMMANDS); // tabs are siblings: no extra step back
        m.tab(GuiModel.MORTALS);
        m.go(GuiModel.CONFIG, null);
        m.go(GuiModel.CONFIG, null); // going where we are adds nothing
        assertTrue(m.back());
        assertEquals(new GuiModel.Route(GuiModel.MORTALS, "main"), m.route());
        assertTrue(m.back());
        assertEquals(GuiModel.PANTHEON, m.route().page());
        assertFalse(m.back(), "nothing before the first page: the screen closes");
    }

    @Test
    void reopeningKeepsThePlace() {
        GuiModel m = opened(true);
        m.go(GuiModel.COMMANDS, "main");
        m.accept(new GuiProtocol.Open("main", true, true, true), 5);
        assertEquals(GuiModel.COMMANDS, m.route().page());
        m.reset();
        m.accept(new GuiProtocol.Open("main", true, true, true), 9);
        assertEquals(GuiModel.PANTHEON, m.route().page());
    }

    @Test
    void aRemovedGodCannotStayOpen() throws Exception {
        GuiModel m = opened(true);
        m.accept(decode(GuiProtocol.Pantheon.class, "gui-pantheon"), 0);
        m.go(GuiModel.MIND, "ysmere");
        GuiProtocol.Pantheon all = decode(GuiProtocol.Pantheon.class, "gui-pantheon");
        m.accept(new GuiProtocol.Pantheon(all.gods().subList(0, 1)), 1);
        assertEquals(GuiModel.PANTHEON, m.route().page());
    }

    @Test
    void anErrorLandsOnTheFieldThatCausedIt() {
        GuiModel m = opened(true);
        String id = m.ask("create", "create_god", 100);
        assertTrue(m.busy("create"));
        assertNull(m.feedback("create", 200));
        m.accept(new GuiProtocol.Result(id, false, "name", "\"Zeus\" is the name of a famous god.", null, null, null), 300);
        assertFalse(m.busy("create"));
        assertEquals("\"Zeus\" is the name of a famous god.", m.error("create", "name", 400));
        assertNull(m.error("create", "archetype", 400));
        assertNull(m.error("create", null, 400));
        assertNull(m.error("other", "name", 400));
        // Asking again clears the old answer.
        String again = m.ask("create", "create_god", 500);
        assertNull(m.error("create", "name", 600));
        m.accept(new GuiProtocol.Result(again, true, null, null, "A new god comes into being.", null, "god5"), 700);
        assertTrue(m.feedback("create", 800).ok());
        assertEquals("god5", m.take("create"));
        assertNull(m.take("create"), "a value is handed over once");
    }

    @Test
    void aMissingConfirmationIsNotAnError() {
        GuiModel m = opened(true);
        String id = m.ask("end", "remove_god", 0);
        m.accept(new GuiProtocol.Result(id, false, null, "Type its name (Vaelor) to confirm.", null, "name", null), 1);
        assertEquals("name", m.feedback("end", 2).needs());
        assertNull(m.error("end", null, 2));
    }

    @Test
    void anUnansweredRequestBecomesAnError() {
        GuiModel m = opened(true);
        m.ask("cmd", "command", 0);
        assertNull(m.feedback("cmd", 7000));
        GuiModel.Feedback f = m.feedback("cmd", 9000);
        assertFalse(f.ok());
        assertFalse(m.busy("cmd"));
    }

    @Test
    void resultsNobodyWaitsForBecomeNotices() {
        GuiModel m = opened(true);
        m.accept(new GuiProtocol.Result("77", false, null, "Only operators may command the god.", null, null, null), 10);
        m.accept(new GuiProtocol.Note("Born: Ysmere.", "info"), 20);
        assertEquals("Born: Ysmere.", m.latest(30, 1000).text());
        assertTrue(m.notices.get(0).error());
        assertNull(m.latest(99_999, 1000));
    }

    @Test
    void commandHistoryWalksUpAndDown() {
        GuiModel m = new GuiModel();
        m.remember("first");
        m.remember("  ");
        m.remember("second");
        m.remember("second");
        assertEquals(List.of("first", "second"), m.history);
        int at = m.history.size();
        at = m.historyStep(at, -1);
        assertEquals("second", m.history.get(at));
        at = m.historyStep(at, -1);
        at = m.historyStep(at, -1);
        assertEquals(0, at);
        at = m.historyStep(m.historyStep(m.historyStep(at, 1), 1), 1);
        assertEquals(m.history.size(), at, "past the newest is the empty line");
        for (int i = 0; i < 80; i++) m.remember("c" + i);
        assertEquals(50, m.history.size());
    }

    @Test
    void journalAppendsWithoutRepeatsAndFilters() throws Exception {
        GuiModel m = opened(true);
        GuiProtocol.Journal first = decode(GuiProtocol.Journal.class, "gui-journal");
        m.accept(first, 0);
        m.accept(new GuiProtocol.Journal("main", false, List.of(first.lines().get(7),
                new GuiProtocol.Line(900, 5, "speech", "announce: \"It is finished.\""))), 1);
        assertEquals(9, m.journal.get("main").size());
        assertEquals(2, m.journalLines("main", "", Set.of("thought", "tool", "info", "error", "cycle")).size());
        assertEquals(2, m.journalLines("main", "KORVATH", Set.of()).size());
        m.accept(new GuiProtocol.Journal("main", true, List.of()), 2);
        assertTrue(m.journal.get("main").isEmpty());
    }

    @Test
    void configSearchLooksThroughEveryTab() throws Exception {
        GuiModel m = opened(true);
        m.accept(decode(GuiProtocol.Config.class, "gui-sample-config-Mind"), 0);
        m.accept(decode(GuiProtocol.Config.class, "gui-sample-config-Voice"), 0);
        assertFalse(m.options("Mind", "").isEmpty());
        assertTrue(m.options("Mind", "").stream().noneMatch(o -> o.key().startsWith("voice.")));
        List<GuiProtocol.Option> hits = m.options(null, "volume");
        assertEquals(1, hits.size());
        assertEquals("voice.tts.volume", hits.get(0).key());
        assertTrue(m.options(null, "no such setting anywhere").isEmpty());
    }

    @Test
    void everyPageSubscribesToAPartThePluginKnows() {
        Set<String> parts = Set.of("pantheon", "god", "commands", "mortals", "journal", "looks", "config", "switches", "costs");
        for (String page : List.of(GuiModel.PANTHEON, GuiModel.OVERVIEW, GuiModel.COMMANDS, GuiModel.MORTALS,
                GuiModel.NATURE, GuiModel.BODY, GuiModel.MIND, GuiModel.JOURNAL, GuiModel.CREATE, GuiModel.SWITCHES,
                GuiModel.CONFIG, GuiModel.COSTS)) {
            assertTrue(parts.contains(GuiModel.part(page)), page);
        }
    }
}
