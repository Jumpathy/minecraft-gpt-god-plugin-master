package net.bigyous.gptgodmc.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.gui.MenuModel.Decision;
import net.bigyous.gptgodmc.gui.MenuModel.Kind;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Mortal;
import net.bigyous.gptgodmc.gui.MenuModel.Page;
import net.bigyous.gptgodmc.gui.MenuModel.State;

class PlayersPageTest {

    @Test
    void theListShowsEveryMortalAsTheirHead() {
        Menu m = MenuLayout.build(Page.PLAYERS, MenuSamples.sample());
        assertEquals("Ashwyn", m.at(MenuLayout.INNER[0]).name());
        assertEquals("Ashwyn", m.at(MenuLayout.INNER[0]).owner());
        assertTrue(String.join(" ", m.at(MenuLayout.INNER[1]).lore()).contains("-7 (despised)"));
        Decision d = MenuLayout.click(m, MenuLayout.INNER[1], null);
        assertEquals(Kind.SELECT_PLAYER, d.run().kind());
        assertEquals("Korvath", d.run().arg());
        assertEquals(Page.PLAYER, d.open());
        assertEquals(Kind.NAME_PLAYER, m.at(MenuLayout.NAME_SLOT).action().kind());
        assertEquals(" ", m.at(MenuLayout.NEXT_SLOT).name(), "one page only");
        assertEquals(Page.MAIN, MenuLayout.click(m, MenuModel.BACK_SLOT, null).open());
    }

    @Test
    void manyMortalsArePaged() {
        List<Mortal> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(new Mortal("P" + i, 0, false, null, null, List.of(), List.of(), 0, null));
        }
        State s = MenuSamples.sample().withPlayers(many, null, 0);
        Menu first = MenuLayout.build(Page.PLAYERS, s);
        assertNotNull(first.at(MenuLayout.NEXT_SLOT));
        assertEquals(" ", first.at(MenuLayout.PREV_SLOT).name(), "only the glass frame there");
        Decision next = MenuLayout.click(first, MenuLayout.NEXT_SLOT, null);
        assertEquals("1", next.run().arg());
        Menu second = MenuLayout.build(Page.PLAYERS, s.withPlayers(many, null, 1));
        assertEquals("P28", second.at(MenuLayout.INNER[0]).name());
        assertNull(second.at(MenuLayout.INNER[12]));
        assertEquals("Previous page", second.at(MenuLayout.PREV_SLOT).name());
    }

    @Test
    void aMortalsPageChangesFavorAndMemories() {
        State s = MenuSamples.sample(); // Korvath, -7, is open
        Menu m = MenuLayout.build(Page.PLAYER, s);
        assertEquals("Vaelor › Korvath", m.title());
        assertEquals("Korvath", m.at(10).owner());
        // Favor buttons say where they lead.
        assertEquals("Despise them", m.at(19).name());
        assertFalse(m.at(19).glow());
        assertTrue(String.join(" ", m.at(19).lore()).contains("After: -10"));
        assertTrue(String.join(" ", m.at(24).lore()).contains("After: -2"));
        assertTrue(String.join(" ", m.at(25).lore()).contains("After: +10 (chosen)"));
        Decision favor = MenuLayout.click(m, 25, null);
        assertEquals(Kind.FAVOR, favor.run().kind());
        assertEquals("set:10", favor.run().arg());
        // Notes and deeds, newest first; erasing one asks first.
        assertTrue(m.at(28).name().startsWith("Note"));
        assertTrue(m.at(37).name().startsWith("Deed"));
        Decision erase = MenuLayout.click(m, 37, null);
        assertNull(erase.run());
        assertEquals(Kind.DELETE_DEED, erase.confirm().kind());
        assertTrue(erase.confirm().arg().endsWith("|killed Ashwyn (Ashwyn was slain by Korvath)"));
        Menu asking = MenuLayout.build(Page.PLAYER, s.withConfirm(erase.confirm()));
        Decision yes = MenuLayout.click(asking, MenuModel.CONFIRM_SLOT, erase.confirm());
        assertEquals(Kind.DELETE_DEED, yes.run().kind());
        assertNull(yes.open());
        // Forgetting asks first, then returns to the list.
        Decision forget = MenuLayout.click(m, 16, null);
        assertEquals(Kind.FORGET_PLAYER, forget.confirm().kind());
        Decision done = MenuLayout.click(MenuLayout.build(Page.PLAYER, s.withConfirm(forget.confirm())),
                MenuModel.CONFIRM_SLOT, forget.confirm());
        assertEquals(Kind.FORGET_PLAYER, done.run().kind());
        assertEquals(Page.PLAYERS, done.open());
        // Notes and deeds typed in chat.
        assertEquals(Kind.ADD_NOTE, MenuLayout.click(m, 12, null).run().kind());
        assertEquals(Kind.ADD_DEED, MenuLayout.click(m, 13, null).run().kind());
        // Back goes to the list, not the main page.
        assertEquals(Page.PLAYERS, MenuLayout.click(m, MenuModel.BACK_SLOT, null).open());
    }

    @Test
    void anUnknownMortalHasAnEmptyRecord() {
        Mortal pip = MenuSamples.mortals().get(2);
        Menu m = MenuLayout.build(Page.PLAYER, MenuSamples.sample().withPlayers(MenuSamples.mortals(), pip, 0));
        assertEquals("No notes", m.at(31).name());
        assertEquals("No deeds", m.at(40).name());
        assertTrue(m.at(22).glow(), "Neutral is where Pip stands");
        assertFalse(m.at(19).glow());
    }

    @Test
    void favorButtonsClamp() {
        assertEquals(10, MenuLayout.favorAfter(8, "add:5"));
        assertEquals(-10, MenuLayout.favorAfter(-7, "add:-5"));
        assertEquals(-10, MenuLayout.favorAfter(4, "set:-10"));
        assertEquals(0, MenuLayout.favorAfter(-3, "set:0"));
        assertEquals(-2, MenuLayout.favorAfter(-1, "add:-1"));
    }
}
