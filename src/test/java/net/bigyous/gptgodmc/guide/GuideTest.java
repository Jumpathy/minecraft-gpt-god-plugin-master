package net.bigyous.gptgodmc.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.client.ModGuide;
import net.bigyous.gptgodmc.client.ModMessages;
import net.bigyous.gptgodmc.client.ModSessions;
import net.bigyous.gptgodmc.commands.GodCommand;
import net.bigyous.gptgodmc.guide.Guide.Book;
import net.bigyous.gptgodmc.guide.Guide.Chapter;
import net.bigyous.gptgodmc.guide.Guide.Context;
import net.bigyous.gptgodmc.guide.Guide.Feature;
import net.bigyous.gptgodmc.guide.Guide.GodLine;

/**
 * The guide's pages: what mortals and operators read, that every command it names exists, that the book
 * fits its pages, and the client mod's guide message (golden files guide-*.json in
 * client-mod/src/test/resources/protocol; write them anew with the environment variable GUI_FIXTURES_WRITE=1).
 */
class GuideTest {
    private static final Path DIR = Path.of("client-mod/src/test/resources/protocol");
    private static final boolean WRITE = System.getenv("GUI_FIXTURES_WRITE") != null;

    static List<GodLine> gods() {
        return List.of(new GodLine("Vaelor", "the Ember Judge", 0xFF5A30, false));
    }

    /** The defaults of config.yml: relics and apotheosis off; voice off (no Simple Voice Chat in tests). */
    static Set<Feature> defaultsOff() {
        return EnumSet.of(Feature.RELICS, Feature.APOTHEOSIS, Feature.VOICE);
    }

    static Book player() {
        return Guide.build(new Context(false, gods(), defaultsOff()));
    }

    static Book operator() {
        return Guide.build(new Context(true, gods(), defaultsOff()));
    }

    private static String all(Book b) {
        StringBuilder sb = new StringBuilder();
        for (Chapter c : b.chapters()) {
            sb.append(c.title()).append('\n');
            for (Guide.Block block : c.blocks()) {
                switch (block) {
                    case Guide.Para p -> sb.append(p.text()).append('\n');
                    case Guide.Heading h -> sb.append(h.text()).append('\n');
                    case Guide.Cmd cmd -> sb.append(cmd.cmd()).append(" : ").append(cmd.text()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    // ---- Content -------------------------------------------------------------------------------------

    @Test
    void mortalsReadAboutTheGodsAndHowToReachThem() {
        Book b = player();
        assertTrue(b.operatorChapters().isEmpty());
        String text = all(b);
        for (String must : List.of("/pray", "/offer", "Sneak + right-click", "/swear", "/commandments", "/gods",
                "/holydays", "/side", "name in chat", "remembers", "favor", "vessel", "Blessings", "Wrath",
                "Building", "Vision", "quests", "title", "halo", "rites", "Music", "Servants",
                "living land", "flourish", "blight", "Oaths", "Godslayer", "/guide")) {
            assertTrue(text.contains(must), "mortal guide lacks " + must);
        }
        for (String never : List.of("setkey", "/god manager", "/god create", "/god command", "4673", "config.yml")) {
            assertFalse(text.contains(never), "mortal guide shows operators' " + never);
        }
    }

    @Test
    void operatorsAlsoReadHowToRunTheGods() {
        Book b = operator();
        assertEquals(player().chapters(), b.playerChapters(), "operators read the same mortal pages first");
        assertEquals(7, b.operatorChapters().size());
        String text = all(Guide.build(new Context(true, gods(), defaultsOff())));
        for (String must : List.of("gptgod setkey", "ANTHROPIC_API_KEY", "/god manager", "nether star",
                "/god create", "/god remove", "realms:", "/god command", "/god say", "/god wake", "/god pause",
                "/god status", "/god journal", "http://localhost:4673", "gptgod usage", "/god reload", "/bonds",
                "/relics", "/godslayer", "anthropic, llm", "guide")) {
            assertTrue(text.contains(must), "operator guide lacks " + must);
        }
        // Operator chapters come after every mortal one.
        int firstOp = b.chapters().indexOf(b.operatorChapters().get(0));
        assertTrue(b.chapters().subList(firstOp, b.chapters().size()).stream().allMatch(Chapter::op));
    }

    @Test
    void switchedOffFeaturesAreLeftOut() {
        Book all = Guide.build(new Context(false, gods(), Set.of()));
        assertTrue(all(all).contains("Relics") && all(all).contains("demigod") && all(all).contains("Simple Voice Chat"));
        Book bare = Guide.build(new Context(false, gods(), EnumSet.allOf(Feature.class)));
        String text = all(bare);
        for (String gone : List.of("/swear", "living land", "Godslayer", "Relics", "Servants", "/holydays",
                "/side", "Missions", "demigod", "Voice Chat")) {
            assertFalse(text.contains(gone), "switched off but still shown: " + gone);
        }
        assertTrue(text.contains("/pray"));
        Book noRecipe = Guide.build(new Context(false, gods(), EnumSet.of(Feature.GODSLAYER_CRAFT)));
        assertTrue(all(noRecipe).contains("Godslayer"));
        assertFalse(all(noRecipe).contains("heavy core"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> godSubcommands() throws Exception {
        Field f = GodCommand.class.getDeclaredField("SUBS");
        f.setAccessible(true);
        List<String> subs = new ArrayList<>((List<String>) f.get(null));
        subs.addAll(List.of("gui", "menu", "guide")); // aliases handled by the same case
        return subs;
    }

    private static Set<String> pluginCommands() throws Exception {
        try (var in = GuideTest.class.getResourceAsStream("/plugin.yml")) {
            YamlConfiguration yml = new YamlConfiguration();
            yml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8)); // throws, never logs
            ConfigurationSection cmds = yml.getConfigurationSection("commands");
            Set<String> out = new HashSet<>();
            for (String name : cmds.getKeys(false)) {
                out.add(name);
                out.addAll(cmds.getStringList(name + ".aliases"));
            }
            return out;
        }
    }

    /** No invented commands: every /god subcommand is GodCommand's, every /command is in plugin.yml. */
    @Test
    void everyCommandItNamesExists() throws Exception {
        String text = all(Guide.build(new Context(true, gods(), Set.of())));
        List<String> subs = godSubcommands();
        Matcher god = Pattern.compile("/god (\\w+)").matcher(text);
        int seen = 0;
        while (god.find()) {
            assertTrue(subs.contains(god.group(1)), "/god " + god.group(1) + " is not a subcommand");
            seen++;
        }
        assertTrue(seen > 20);
        Set<String> commands = pluginCommands();
        Matcher slash = Pattern.compile("(?<![\\w/:.])/([a-z]+)").matcher(text);
        while (slash.find()) {
            assertTrue(commands.contains(slash.group(1)), "/" + slash.group(1) + " is not in plugin.yml");
        }
        assertTrue(commands.contains("guide") && commands.contains("tutorial"));
        assertTrue(subs.contains("tutorial"));
    }

    // ---- The book ------------------------------------------------------------------------------------

    @Test
    void everyPageFitsTheBook() {
        for (Book b : List.of(player(), operator(), Guide.build(new Context(true, gods(), Set.of())))) {
            List<GuideBook.Page> pages = GuideBook.pages(b);
            assertTrue(pages.size() < GuideBook.MAX_PAGES, "pages: " + pages.size());
            for (int i = 0; i < pages.size(); i++) {
                List<GuideBook.Line> lines = pages.get(i).lines();
                assertTrue(lines.size() <= GuideBook.LINES, "page " + (i + 1) + " has " + lines.size() + " lines");
                assertFalse(lines.isEmpty() || lines.get(lines.size() - 1).blank(), "page " + (i + 1) + " ends blank");
                for (GuideBook.Line l : lines) {
                    assertTrue(l.width() <= GuideBook.WIDTH, "too wide on page " + (i + 1) + ": " + l.text());
                }
            }
            assertEquals(pages.size(), GuideBook.components(pages).size());
        }
        assertTrue(GuideBook.pages(operator()).size() > GuideBook.pages(player()).size());
    }

    @Test
    void contentsLinksTurnToEachChapter() {
        Book b = operator();
        List<GuideBook.Page> pages = GuideBook.pages(b);
        List<Integer> starts = GuideBook.starts(b);
        List<Integer> links = new ArrayList<>();
        for (GuideBook.Page p : pages.subList(1, starts.get(0) - 1)) { // the contents pages
            for (GuideBook.Line l : p.lines()) {
                for (GuideBook.Run r : l.runs()) {
                    if (r.link() > 0 && (links.isEmpty() || links.get(links.size() - 1) != r.link())) links.add(r.link());
                }
            }
        }
        assertEquals(starts, links);
        for (int i = 0; i < b.chapters().size(); i++) {
            String first = pages.get(starts.get(i) - 1).lines().get(0).text();
            assertTrue(b.chapters().get(i).title().startsWith(first), first + " vs " + b.chapters().get(i).title());
        }
        assertTrue(pages.get(0).text().contains("Vaelor") && pages.get(0).text().contains("Operator's edition"));
        assertFalse(GuideBook.pages(player()).get(0).text().contains("Operator's edition"));
    }

    @Test
    void wrappingUsesTheFontsWidths() {
        assertEquals(4, GuideBook.advance(' '));
        assertEquals(2, GuideBook.advance('i'));
        assertEquals(6, GuideBook.advance('m'));
        assertEquals(List.of("aaa", "bbb"), GuideBook.wrap("aaa bbb", GuideBook.width("aaa b", false), false));
        List<String> cut = GuideBook.wrap("x".repeat(40), GuideBook.WIDTH, false);
        assertTrue(cut.size() == 3 && cut.get(0).length() == GuideBook.WIDTH / 6);
        // Pale gods are darkened to read on paper; dark ones are left alone.
        assertTrue(GuideBook.ink(0xFFFFFF) < 0x808080);
        assertEquals(0x14457A, GuideBook.ink(0x14457A));
    }

    // ---- The client mod's message ----------------------------------------------------------------------

    private static void golden(String name, JsonObject message) throws Exception {
        Path file = DIR.resolve(name + ".json");
        String pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(message) + "\n";
        if (WRITE || !Files.exists(file)) {
            Files.writeString(file, pretty, StandardCharsets.UTF_8);
        }
        assertEquals(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)), message, name);
    }

    @Test
    void modMessageMatchesTheGoldenFiles() throws Exception {
        JsonObject p = ModGuide.message(player(), true);
        JsonObject o = ModGuide.message(operator(), false);
        golden("guide-player", p);
        golden("guide-operator", o);
        assertEquals("guide", p.get("t").getAsString());
        assertTrue(p.get("open").getAsBoolean() && !o.get("open").getAsBoolean());
        assertEquals("#FF5A30", p.getAsJsonArray("gods").get(0).getAsJsonObject().get("color").getAsString());
        assertEquals(player().chapters().size(), p.getAsJsonArray("chapters").size());
        assertFalse(p.toString().contains("\"op\":true"));
        assertTrue(o.toString().contains("\"op\":true"));
        // Both fit one plugin message with room to grow.
        assertTrue(ModMessages.bytes(o).length < ModSessions.MAX_BYTES * 3 / 4, "bytes: " + ModMessages.bytes(o).length);
        assertTrue(ModSessions.SERVER_FEATURES.contains(ModSessions.GUIDE));
    }
}
