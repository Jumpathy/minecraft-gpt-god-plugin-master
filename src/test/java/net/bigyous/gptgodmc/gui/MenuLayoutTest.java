package net.bigyous.gptgodmc.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.gui.MenuModel.Action;
import net.bigyous.gptgodmc.gui.MenuModel.Decision;
import net.bigyous.gptgodmc.gui.MenuModel.Icon;
import net.bigyous.gptgodmc.gui.MenuModel.Kind;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Page;
import net.bigyous.gptgodmc.gui.MenuModel.State;

class MenuLayoutTest {

    @Test
    void everyPageFitsTheChestAndNamesEveryIcon() {
        State s = MenuSamples.sample();
        for (Page page : Page.values()) {
            Menu m = MenuLayout.build(page, s);
            assertFalse(m.title().isBlank(), page.name());
            assertTrue(m.title().length() <= 32, "title too long: " + m.title());
            for (Map.Entry<Integer, Icon> e : m.icons().entrySet()) {
                assertTrue(e.getKey() >= 0 && e.getKey() < MenuModel.SIZE, page + " slot " + e.getKey());
                Icon icon = e.getValue();
                assertNotNull(icon.name(), page + " slot " + e.getKey());
                assertNotNull(icon.action(), page + " slot " + e.getKey());
                if (icon.action().kind() != Kind.NONE) {
                    assertFalse(icon.name().isBlank(), page + " slot " + e.getKey() + " needs a name");
                }
                for (String line : icon.lore()) {
                    assertTrue(line.length() <= 36, "lore too long on " + page + ": " + line);
                }
            }
            assertEquals(Kind.CLOSE, m.at(MenuModel.CLOSE_SLOT).action().kind());
            assertEquals(Material.NETHER_STAR, m.at(MenuModel.HEADER_SLOT).material());
            // Back leads to the page's parent (the hub has none); Home to the hub on deeper pages.
            if (page.parent == null) {
                assertNull(MenuLayout.click(m, MenuModel.BACK_SLOT, null).open(), "the hub has no Back");
            } else {
                assertEquals(page.parent, MenuLayout.click(m, MenuModel.BACK_SLOT, null).open(), page.name());
            }
            assertEquals(page.depth() >= 2, m.at(MenuModel.HOME_SLOT) != null
                    && MenuLayout.click(m, MenuModel.HOME_SLOT, null).open() == Page.HUB, page.name());
            // Nothing about tokens or money, anywhere.
            for (Icon icon : m.icons().values()) {
                String all = (icon.name() + " " + String.join(" ", icon.lore())).toLowerCase();
                assertFalse(all.contains("token") || all.contains("cost") || all.contains("$"), all);
            }
        }
    }

    @Test
    void mainPageLeadsToEveryOtherPage() {
        Menu m = MenuLayout.build(Page.MAIN, MenuSamples.sample());
        Set<Page> reached = new HashSet<>();
        for (int slot = 0; slot < MenuModel.SIZE; slot++) {
            Decision d = MenuLayout.click(m, slot, null);
            if (d.open() != null) reached.add(d.open());
        }
        assertEquals(Set.of(Page.HUB, Page.PERSONALITY, Page.DIALS, Page.MIGHT, Page.MOOD, Page.PRESENCE, Page.LOOKS,
                Page.VOICE, Page.COMMAND, Page.PLAYERS, Page.BONDS), reached, "a god's page leads only to its own pages (and Back)");
        assertEquals(Kind.WAKE, MenuLayout.click(m, MenuLayout.WAKE_SLOT, null).run().kind());
        assertEquals("Pause thoughts", m.at(MenuLayout.PAUSE_SLOT).name());
        State paused = new State("V", null, "evil", true, true, false, false, null, MenuSamples.sample().archetypes(),
                MenuSamples.sample().pending(), Map.of(), null, null);
        assertEquals("Resume thoughts", MenuLayout.build(Page.MAIN, paused).at(MenuLayout.PAUSE_SLOT).name());
        String header = String.join(" ", m.at(MenuModel.HEADER_SLOT).lore());
        assertTrue(header.contains("Archetype: wrathful") && header.contains("walks the world"), header);
    }

    @Test
    void framesAndEmptySlotsDoNothing() {
        Menu m = MenuLayout.build(Page.MOOD, MenuSamples.sample());
        Decision d = MenuLayout.click(m, 0, null);
        assertNull(d.run());
        assertNull(d.open());
        assertNull(MenuLayout.click(m, 10, null).run(), "an empty inner slot");
        assertNull(MenuLayout.click(m, 999, null).run(), "outside the chest");
    }

    @Test
    void personalityNeedsASecondClickToConfirm() {
        State s = MenuSamples.sample();
        Menu m = MenuLayout.build(Page.PERSONALITY, s);
        Icon current = m.at(MenuLayout.INNER[4]);
        assertEquals("Wrathful", current.name());
        assertTrue(current.glow(), "the current archetype glows");
        assertEquals(Material.ORANGE_WOOL, current.material());
        assertEquals(Material.RED_WOOL, m.at(MenuLayout.INNER[3]).material());
        assertFalse(m.at(MenuLayout.INNER[3]).glow());
        assertTrue(m.at(MenuLayout.INNER[6]).material().name().endsWith("_WOOL"), "unknown archetypes still get a wool");

        Decision first = MenuLayout.click(m, MenuLayout.INNER[3], null);
        assertNull(first.run(), "the first click only asks");
        assertNotNull(first.confirm());
        assertEquals("evil", first.confirm().arg());

        Menu asking = MenuLayout.build(Page.PERSONALITY, s.withConfirm(first.confirm()));
        assertEquals(Kind.CONFIRM, asking.at(MenuModel.CONFIRM_SLOT).action().kind());
        assertTrue(String.join(" ", asking.at(MenuModel.CONFIRM_SLOT).lore()).contains("evil"));
        Decision second = MenuLayout.click(asking, MenuModel.CONFIRM_SLOT, first.confirm());
        assertEquals(Kind.PERSONA, second.run().kind());
        assertEquals("evil", second.run().arg());
        assertNull(second.confirm());

        Decision cancel = MenuLayout.click(asking, MenuModel.CANCEL_SLOT, first.confirm());
        assertNull(cancel.run());
        assertNull(cancel.confirm());
        assertEquals(Kind.NONE, MenuLayout.build(Page.PERSONALITY, s).at(MenuModel.CONFIRM_SLOT).action().kind(),
                "no confirm icon unless asked");
        assertNull(MenuLayout.click(m, MenuModel.CONFIRM_SLOT, null).run(), "a stale confirm does nothing");

        Decision random = MenuLayout.click(m, 40, null);
        assertEquals("random", random.confirm().arg());
    }

    @Test
    void moodButtonsMakeTheGodFeelIt() {
        Menu m = MenuLayout.build(Page.MOOD, MenuSamples.sample());
        Icon wrath = m.at(20);
        assertEquals("Make wrathful", wrath.name());
        assertTrue(wrath.glow());
        assertEquals(Material.RED_CONCRETE, wrath.material());
        assertEquals("Calm the god", m.at(31).name());
        Action a = MenuLayout.click(m, 21, null).run();
        assertEquals(Kind.MOOD, a.kind());
        assertEquals("joyful", a.arg());
        for (VesselMood mood : VesselMood.values()) {
            assertNotNull(m.at(MenuLayout.MOOD_SLOTS.get(mood)), mood.id());
            String cmd = MenuLayout.moodCommand(mood, 10);
            if (mood != VesselMood.CALM) {
                assertTrue(cmd.startsWith("For the next 10 minutes you are " + mood.name() + ":"), cmd);
            }
        }
    }

    @Test
    void presenceSummonsToTheViewerAndConfirmsTheTrueForm() {
        Menu m = MenuLayout.build(Page.PRESENCE, MenuSamples.sample());
        assertTrue(m.at(20).viewerHead());
        assertEquals(Kind.SUMMON, MenuLayout.click(m, 20, null).run().kind());
        assertEquals(Kind.FOLLOW, MenuLayout.click(m, 22, null).run().kind());
        assertEquals(Kind.VANISH, MenuLayout.click(m, 24, null).run().kind());
        Decision reveal = MenuLayout.click(m, 30, null);
        assertNull(reveal.run());
        assertEquals(Kind.TRUE_FORM, reveal.confirm().kind());
        assertTrue(reveal.confirm().confirm().contains("strikes down"), "the sample server's true form smites");
        assertEquals(Kind.END_TRUE_FORM, MenuLayout.click(m, 32, null).run().kind());
    }

    @Test
    void presenceSwitchesTheLookAndMarksTheCurrentOne() {
        Menu main = MenuLayout.build(Page.MAIN, MenuSamples.sample());
        assertEquals(Page.LOOKS, MenuLayout.click(main, MenuLayout.LOOKS_SLOT, null).open());
        Menu m = MenuLayout.build(Page.LOOKS, MenuSamples.sample());
        java.util.List<String> order = MenuLayout.lookOrder();
        assertTrue(order.size() <= MenuLayout.INNER.length, "every look fits on one page");
        assertEquals("auto", MenuLayout.click(m, MenuLayout.INNER[0], null).run().arg());
        for (int i = 0; i < order.size(); i++) {
            assertEquals(Kind.LOOK, MenuLayout.click(m, MenuLayout.INNER[i], null).run().kind());
            assertEquals(order.get(i), MenuLayout.click(m, MenuLayout.INNER[i], null).run().arg());
            assertEquals(order.get(i).equals("radiant"), m.at(MenuLayout.INNER[i]).glow(), order.get(i));
        }
        assertTrue(order.contains("thunder"), "the new looks are offered");
        assertEquals(Page.MAIN, MenuLayout.click(m, MenuModel.BACK_SLOT, null).open());
    }

    @Test
    void theNaturePageTurnsDialsWithinZeroToTen() {
        Menu main = MenuLayout.build(Page.MAIN, MenuSamples.sample());
        assertEquals(Page.DIALS, MenuLayout.click(main, MenuLayout.NATURE_SLOT, null).open());
        Menu m = MenuLayout.build(Page.DIALS, MenuSamples.sample());
        int gen = MenuLayout.dialSlot(0);
        assertEquals("generosity:1", MenuLayout.click(m, gen, null, false, false).run().arg());
        assertEquals("generosity:-1", MenuLayout.click(m, gen, null, true, false).run().arg());
        assertEquals("generosity:reset", MenuLayout.click(m, gen, null, true, true).run().arg());
        assertEquals("vanity:-1", MenuLayout.click(m, MenuLayout.dialSlot(9), null, true, false).run().arg());
        assertTrue(m.at(gen).lore().get(0).startsWith("■■■□"), m.at(gen).lore().toString());
        assertTrue(m.at(gen).glow(), "changed from its archetype");
        // A right-click elsewhere is an ordinary click.
        assertEquals(Page.MAIN, MenuLayout.click(m, MenuModel.BACK_SLOT, null, true, false).open());
        assertEquals("reset", MenuLayout.click(m, MenuLayout.DIAL_RESET_SLOT, null).run().arg());
        assertEquals(Page.MAIN, MenuLayout.click(m, MenuModel.BACK_SLOT, null).open());
        assertEquals(10, MenuLayout.dialAfter(10, 9, "1"));
        assertEquals(0, MenuLayout.dialAfter(0, 1, "-1"));
        assertEquals(9, MenuLayout.dialAfter(3, 9, "reset"));
        assertEquals(4, MenuLayout.dialAfter(3, 9, "1"));
    }

    @Test
    void theMoodPageShowsAndCalmsTheHeart() {
        Menu m = MenuLayout.build(Page.MOOD, MenuSamples.sample());
        Icon heart = m.at(MenuLayout.HEART_SLOT);
        assertEquals(Kind.CALM_HEART, MenuLayout.click(m, MenuLayout.HEART_SLOT, null).run().kind());
        assertTrue(heart.lore().stream().anyMatch(l -> l.contains("strong wrath")), heart.lore().toString());
        assertTrue(MenuLayout.build(Page.MAIN, MenuSamples.sample()).at(MenuModel.HEADER_SLOT).lore().stream()
                .anyMatch(l -> l.startsWith("Heart: strong wrath")));
    }

    @Test
    void theVoicePagePicksAVoiceAndAnEffect() {
        Menu main = MenuLayout.build(Page.MAIN, MenuSamples.sample());
        assertEquals(Page.VOICE, MenuLayout.click(main, MenuLayout.VOICE_SLOT, null).open());
        Menu m = MenuLayout.build(Page.VOICE, MenuSamples.sample());
        assertEquals(Kind.VOICE, MenuLayout.click(m, 11, null).run().kind());
        assertEquals("am_onyx", MenuLayout.click(m, 11, null).run().arg());
        assertEquals("demonic", MenuLayout.click(m, 32, null).run().arg());
        assertEquals("auto", MenuLayout.click(m, 40, null).run().arg());
        assertEquals(Kind.VOICE_SAMPLE, MenuLayout.click(m, MenuLayout.SAMPLE_SLOT, null).run().kind());
        assertTrue(m.at(15).glow(), "the sample god speaks with bm_george");
        assertTrue(m.at(34).glow(), "and its effect is ethereal");
        assertTrue(!m.at(10).glow());
    }

    @Test
    void commandPageDeliversPresetsAndCancels() {
        Menu m = MenuLayout.build(Page.COMMAND, MenuSamples.sample());
        assertEquals(Kind.DELIVER, MenuLayout.click(m, 10, null).run().kind());
        assertEquals("speak", MenuLayout.click(m, 11, null).run().arg());
        assertEquals("bless", MenuLayout.click(m, 12, null).run().arg());
        assertEquals("punish_killer", MenuLayout.click(m, 13, null).run().arg());
        Decision tf = MenuLayout.click(m, 16, null);
        assertNull(tf.run(), "revealing the true form to all is confirmed first");
        assertEquals("true_form", tf.confirm().arg());
        assertEquals("#3", m.at(28).name());
        assertEquals(Material.PAPER, m.at(28).material());
        assertEquals(Material.CLOCK, m.at(29).material());
        Action cancel = MenuLayout.click(m, 28, null).run();
        assertEquals(Kind.CANCEL_COMMAND, cancel.kind());
        assertEquals("3", cancel.arg());
        assertEquals("Pending commands: 2", m.at(22).name());
    }

    @Test
    void settingsShowTheirStateAndToggle() {
        Menu m = MenuLayout.build(Page.SETTINGS, MenuSamples.sample());
        Icon body = m.at(MenuLayout.INNER[0]);
        assertEquals("The god's body: ON", body.name());
        assertEquals(Material.LIME_CONCRETE, body.material());
        Icon wake = m.at(MenuLayout.INNER[1]);
        assertEquals("Body on waking: OFF", wake.name());
        assertEquals(Material.RED_CONCRETE, wake.material());
        Action a = MenuLayout.click(m, MenuLayout.INNER[1], null).run();
        assertEquals(Kind.TOGGLE_SETTING, a.kind());
        assertEquals("vessel.manifest-on-wake", a.arg());
        assertEquals(MenuLayout.SETTINGS.size(), m.icons().values().stream()
                .filter(i -> i.action().kind() == Kind.TOGGLE_SETTING).count());
    }

    @Test
    void theBondsPageSetsAndNudgesEachPeer() {
        Menu main = MenuLayout.build(Page.MAIN, MenuSamples.sample());
        assertEquals(Page.BONDS, MenuLayout.click(main, MenuLayout.BONDS_SLOT, null).open());
        State alone = MenuSamples.sample().withGods(MenuModel.Gods.NONE);
        assertNull(MenuLayout.build(Page.MAIN, alone).at(MenuLayout.BONDS_SLOT), "no Bonds for a lone god");
        Menu m = MenuLayout.build(Page.BONDS, MenuSamples.sample());
        int peer = MenuLayout.bondPeerSlot(0);
        assertEquals("Ysmere", m.at(peer).name());
        assertEquals("ysmere|+20", MenuLayout.click(m, peer, null, false, false).run().arg());
        assertEquals("ysmere|-20", MenuLayout.click(m, peer, null, true, false).run().arg());
        assertEquals("ysmere|allies", MenuLayout.click(m, MenuLayout.bondPresetSlot(0, 0), null).run().arg());
        assertTrue(m.at(MenuLayout.bondPresetSlot(0, 3)).glow(), "they are rivals now");
        assertEquals("god3|enemies", MenuLayout.click(m, MenuLayout.bondPresetSlot(1, 4), null).run().arg());
        assertEquals(Page.MAIN, MenuLayout.click(m, MenuModel.BACK_SLOT, null).open());
    }

    @Test
    void loreWrapsAndTruncates() {
        assertEquals(1, MenuLayout.wrap("short").size());
        var lines = MenuLayout.wrap("word ".repeat(80), 3);
        assertEquals(3, lines.size());
        assertTrue(lines.get(2).endsWith("…"));
        assertEquals("Wrathful", MenuLayout.title("wrathful"));
    }
}
