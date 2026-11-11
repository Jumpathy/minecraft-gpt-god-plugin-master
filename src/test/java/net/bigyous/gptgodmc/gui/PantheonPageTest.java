package net.bigyous.gptgodmc.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.gui.MenuModel.Action;
import net.bigyous.gptgodmc.gui.MenuModel.Decision;
import net.bigyous.gptgodmc.gui.MenuModel.Kind;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Page;
import net.bigyous.gptgodmc.gui.MenuModel.State;

/** The hub lists every god and picks which one you edit; Might & Mind sets its power, model and effort. */
class PantheonPageTest {

    @Test
    void theHubListsEveryGodAndPicksOne() {
        Menu m = MenuLayout.build(Page.HUB, MenuSamples.sample());
        assertEquals("Vaelor", m.at(MenuLayout.INNER[0]).name());
        assertTrue(m.at(MenuLayout.INNER[0]).glow(), "awake gods are marked");
        assertEquals("Ysmere", m.at(MenuLayout.INNER[1]).name());
        assertTrue(m.at(MenuLayout.INNER[2]).name().contains("@god3"), "an unborn god shows its id");
        Decision pick = MenuLayout.click(m, MenuLayout.INNER[1], null);
        assertEquals(Kind.SELECT_GOD, pick.run().kind());
        assertEquals("ysmere", pick.run().arg());
        assertEquals(Page.MAIN, pick.open());
        assertEquals(Page.CREATE, MenuLayout.click(m, MenuLayout.CREATE_GOD_SLOT, null).open());
        assertEquals(Page.SETTINGS, MenuLayout.click(m, MenuLayout.SWITCHES_SLOT, null).open());
        assertEquals(Page.ADVANCED, MenuLayout.click(m, MenuLayout.CONFIG_SLOT, null).open());
        assertEquals("God Manager", m.title());
    }

    @Test
    void aServerWithOneGodStillHasAHub() {
        State alone = MenuSamples.sample().withGods(MenuModel.Gods.NONE);
        Menu m = MenuLayout.build(Page.HUB, alone);
        assertEquals("Vaelor", m.at(MenuLayout.INNER[0]).name());
        assertEquals(Page.MAIN, MenuLayout.click(m, MenuLayout.INNER[0], null).open());
        assertEquals(Page.HUB, MenuLayout.click(MenuLayout.build(Page.MAIN, alone), MenuModel.BACK_SLOT, null).open());
    }

    @Test
    void titlesAreBreadcrumbs() {
        State s = MenuSamples.sample();
        assertEquals("Vaelor", MenuLayout.build(Page.MAIN, s).title());
        assertEquals("Vaelor › Mood & heart", MenuLayout.build(Page.MOOD, s).title());
        assertEquals("Vaelor › Korvath", MenuLayout.build(Page.PLAYER, s).title());
        assertEquals("God Manager › Server config", MenuLayout.build(Page.ADVANCED, s).title());
        assertEquals("God Manager › New god", MenuLayout.build(Page.CREATE, s).title());
    }

    @Test
    void mightAndMindSetPowerModelAndEffort() {
        Menu m = MenuLayout.build(Page.MIGHT, MenuSamples.sample());
        assertTrue(m.at(15).glow(), "the sample god is supreme");
        assertEquals(new Action(Kind.POWER, "lesser", null), MenuLayout.click(m, 11, null).run());
        assertTrue(m.at(25).glow(), "it thinks with its own model (opus)");
        assertFalse(m.at(19).glow());
        assertEquals("default", MenuLayout.click(m, 19, null).run().arg());
        assertTrue(m.at(29).glow(), "its effort follows the server");
        assertEquals("high", MenuLayout.click(m, 33, null).run().arg());
        assertNull(m.at(MenuLayout.REMOVE_GOD_SLOT), "erasing lives on the god's page, not here");
        Menu main = MenuLayout.build(Page.MAIN, MenuSamples.sample());
        assertEquals("Erase this god", main.at(MenuLayout.REMOVE_GOD_SLOT).name(), "the first god is erased, not removed");
        assertEquals(Kind.PERSONA, MenuLayout.click(main, MenuLayout.REMOVE_GOD_SLOT, null).confirm().kind());
    }

    @Test
    void removingACreatedGodAsksFirstThenReturnsToTheHub() {
        MenuModel.Gods g = MenuSamples.gods();
        State created = MenuSamples.sample().withGods(new MenuModel.Gods(g.list(), "greater", null, null,
                g.configModel(), g.configEffort(), true));
        Menu m = MenuLayout.build(Page.MAIN, created);
        Decision first = MenuLayout.click(m, MenuLayout.REMOVE_GOD_SLOT, null);
        assertNull(first.run(), "the first click only asks");
        assertEquals(Kind.REMOVE_GOD, first.confirm().kind());
        Menu asking = MenuLayout.build(Page.MAIN, created.withConfirm(first.confirm()));
        Decision done = MenuLayout.click(asking, MenuModel.CONFIRM_SLOT, first.confirm());
        assertEquals(Kind.REMOVE_GOD, done.run().kind());
        assertEquals(Page.HUB, done.open());
    }

    @Test
    void modelNamesAreShort() {
        assertEquals("sonnet 5.5", MenuLayout.shortModel("claude-sonnet-5-5"));
        assertEquals(List.of("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5"), MenuLayout.MODELS);
    }
}
