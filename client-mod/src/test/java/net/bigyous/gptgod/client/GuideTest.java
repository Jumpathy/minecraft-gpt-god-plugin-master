package net.bigyous.gptgod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.gui.GuideProtocol;

/**
 * The guide screen's protocol: decoding the plugin's golden guide messages (the plugin's GuideTest writes
 * them), refusing broken ones, and what a click on a command types into chat.
 */
class GuideTest {
    private static final Path DIR = Path.of("src/test/resources/protocol");

    private static byte[] golden(String name) throws Exception {
        return Files.readAllBytes(DIR.resolve(name + ".json"));
    }

    @Test
    void playerGuideDecodes() throws Exception {
        byte[] data = golden("guide-player");
        assertTrue(GuideProtocol.mine(data));
        GuideProtocol.Guide g = GuideProtocol.decode(data);
        assertNotNull(g);
        assertTrue(g.open());
        assertFalse(g.operator());
        assertEquals("The Book of the Gods", g.title());
        assertEquals(1, g.gods().size());
        assertEquals("Vaelor", g.gods().get(0).name());
        assertEquals(0xFF5A30, g.gods().get(0).color());
        assertTrue(g.operatorChapters().isEmpty());
        assertEquals("What the gods are", g.chapters().get(0).title());
        List<String> kinds = g.chapters().stream().flatMap(c -> c.blocks().stream()).map(GuideProtocol.Block::kind)
                .distinct().toList();
        assertTrue(kinds.containsAll(List.of("p", "h", "cmd")));
        assertTrue(g.chapters().stream().anyMatch(c -> c.blocks().stream()
                .anyMatch(b -> "/pray <words>".equals(b.cmd()) && !b.text().isEmpty())));
    }

    @Test
    void operatorGuideHasItsOwnChaptersLast() throws Exception {
        GuideProtocol.Guide g = GuideProtocol.decode(golden("guide-operator"));
        assertNotNull(g);
        assertFalse(g.open());
        assertTrue(g.operator());
        assertEquals(7, g.operatorChapters().size());
        int first = g.chapters().indexOf(g.operatorChapters().get(0));
        assertEquals(g.playerChapters().size(), first);
        assertEquals(GuideProtocol.decode(golden("guide-player")).chapters(), g.playerChapters());
    }

    @Test
    void brokenGuidesAreRefused() {
        for (String bad : List.of("", "[]", "{\"t\":\"guide\"}", "{\"t\":\"guide\",\"chapters\":[]}",
                "{\"t\":\"guide\",\"chapters\":[{\"title\":\"\"}]}", "{\"t\":\"gui_open\"}", "{\"t\":\"guide\",\"chapters\":5}",
                "not json")) {
            assertNull(GuideProtocol.decode(bad.getBytes(StandardCharsets.UTF_8)), bad);
        }
        // Not ours at all: the God Manager's messages are left to it.
        assertFalse(GuideProtocol.mine("{\"t\":\"gui_open\",\"realm\":\"main\"}".getBytes(StandardCharsets.UTF_8)));
        // Odd fields are clipped or dropped, not trusted.
        GuideProtocol.Guide g = GuideProtocol.decode(("{\"t\":\"guide\",\"gods\":[{\"name\":\"X§cY\",\"color\":\"red\"}],"
                + "\"chapters\":[{\"title\":\"T\",\"blocks\":[{\"p\":\"" + "a".repeat(5000) + "\"},{\"zz\":1}]}]}")
                .getBytes(StandardCharsets.UTF_8));
        assertNotNull(g);
        assertEquals("XcY", g.gods().get(0).name());
        assertEquals(0xB070FF, g.gods().get(0).color());
        assertEquals(1, g.chapters().get(0).blocks().size());
        assertEquals(800, g.chapters().get(0).blocks().get(0).text().length());
    }

    @Test
    void clickingACommandTypesItsStart() {
        assertEquals("/pray ", GuideProtocol.typed("/pray <words>"));
        assertEquals("/swear ", GuideProtocol.typed("/swear [god,] [words]"));
        assertEquals("/god manager item ", GuideProtocol.typed("/god manager item"));
        assertEquals("/god model ", GuideProtocol.typed("/god model | effort"));
        assertEquals("/gods ", GuideProtocol.typed("/gods"));
    }

    @Test
    void theModAsksForTheGuide() {
        assertTrue(new ModConfig().features().contains("guide"));
        ModConfig vanilla = new ModConfig();
        vanilla.vanilla = true;
        assertFalse(vanilla.features().contains("guide"));
    }
}
