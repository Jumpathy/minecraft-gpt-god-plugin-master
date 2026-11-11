package net.bigyous.gptgodmc.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.gui.MenuModel.Kind;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Page;

/** The Advanced page turns config.yml settings with clicks, within their limits. */
class AdvancedTest {
    @Test
    void everySettingIsWellFormedAndFitsItsTab() {
        Set<String> keys = new HashSet<>();
        for (String tab : Advanced.TABS) {
            assertTrue(Advanced.of(tab).size() <= MenuLayout.TUNE_SLOTS.length, tab + " has too many settings");
        }
        for (Advanced.Option o : Advanced.OPTIONS) {
            assertTrue(keys.add(o.key()), "duplicate " + o.key());
            assertTrue(Advanced.TABS.contains(o.tab()), o.key());
            assertTrue(Advanced.controls(o).length() <= 36, o.key());
            if (o.type() == Advanced.Type.CHOICE) assertTrue(o.choices().contains(o.fallback()), o.key());
            if (o.type() == Advanced.Type.INT || o.type() == Advanced.Type.DOUBLE) {
                double f = Double.parseDouble(o.fallback());
                assertTrue(f >= o.min() && f <= o.max(), o.key() + " default out of range");
            }
        }
    }

    @Test
    void clicksFlipStepAndCycle() {
        Advanced.Option chat = Advanced.find("brain.wake-on-any-chat");
        assertEquals("true", Advanced.after(chat, "false", false, false));
        Advanced.Option steps = Advanced.find("brain.max-steps");
        assertEquals("7", Advanced.after(steps, "6", false, false));
        assertEquals("5", Advanced.after(steps, "6", true, false));
        assertEquals("9", Advanced.after(steps, "6", false, true));
        assertEquals("12", Advanced.after(steps, "11", false, true), "clamped at the top");
        assertEquals("1", Advanced.after(steps, "2", true, true), "clamped at the bottom");
        Advanced.Option volume = Advanced.find("voice.tts.volume");
        assertEquals("1.1", Advanced.after(volume, "1.0", false, false));
        Advanced.Option effort = Advanced.find("anthropic.effort");
        assertEquals("low", Advanced.after(effort, "auto", false, false));
        assertEquals("high", Advanced.after(effort, "auto", true, false), "right-click goes back around");
        assertEquals("never", Advanced.show(Advanced.find("brain.idle-seconds"), "0"));
        assertEquals("ON", Advanced.show(chat, "true"));
    }

    @Test
    void thePageShowsTabsAndTurnsSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("brain.wake-on-any-chat", "true");
        Menu m = MenuLayout.build(Page.ADVANCED,
                MenuSamples.sample().withTuning(new MenuModel.Tuning("Mind", values)));
        assertTrue(m.at(10).glow(), "the Mind tab is shown");
        assertEquals("Pace", MenuLayout.click(m, 11, null).run().arg());
        assertEquals(Kind.TAB, MenuLayout.click(m, 11, null).run().kind());
        var any = m.at(20);
        assertNotNull(any);
        assertEquals("Answer every message: ON", any.name());
        assertTrue(any.glow(), "changed from its default");
        assertEquals("brain.wake-on-any-chat|RS", MenuLayout.click(m, 20, null, true, true).run().arg());
        assertEquals(Kind.TUNE, MenuLayout.click(m, 20, null, false, false).run().kind());
        Menu body = MenuLayout.build(Page.ADVANCED,
                MenuSamples.sample().withTuning(new MenuModel.Tuning("Body", Map.of())));
        assertEquals("Body: ON", body.at(19).name());
    }
}
