package net.bigyous.gptgodmc.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.GodNames;
import net.bigyous.gptgodmc.gui.MenuModel.Kind;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Page;

/** Making a god is clicks (nature, power) plus, if wanted, one word typed for its name. */
class CreatePageTest {
    @Test
    void theHubLeadsToTheNewGodPage() {
        Menu p = MenuLayout.build(Page.HUB, MenuSamples.sample());
        assertEquals(Page.CREATE, MenuLayout.click(p, MenuLayout.CREATE_GOD_SLOT, null).open());
    }

    @Test
    void natureAndPowerAreClickedAndTheNameIsOptional() {
        Menu m = MenuLayout.build(Page.CREATE,
                MenuSamples.sample().withDraft(new MenuModel.Draft(null, "random", "greater")));
        assertEquals(Kind.DRAFT_ARCHETYPE, MenuLayout.click(m, 10, null).run().kind());
        assertEquals("lesser", MenuLayout.click(m, 29, null).run().arg());
        assertTrue(m.at(31).glow(), "greater is chosen");
        assertEquals("Give it a name", m.at(MenuLayout.DRAFT_NAME_SLOT).name());
        assertTrue(m.at(MenuLayout.DRAFT_NO_NAME_SLOT).glow(), "it names itself by default");
        assertEquals(Kind.DRAFT_CREATE, MenuLayout.click(m, MenuLayout.DRAFT_CREATE_SLOT, null).run().kind());
        assertEquals(Page.HUB, MenuLayout.click(m, MenuModel.BACK_SLOT, null).open());
        Menu named = MenuLayout.build(Page.CREATE,
                MenuSamples.sample().withDraft(new MenuModel.Draft("Zeus", "wrathful", "supreme")));
        assertEquals("Name: Zeus", named.at(MenuLayout.DRAFT_NAME_SLOT).name());
        assertNotNull(named.at(MenuLayout.DRAFT_CREATE_SLOT).lore().stream().filter(l -> l.contains("Zeus")).findAny()
                .orElse(null));
    }

    @Test
    void theOperatorMayNameAGodAnythingSensible() {
        assertNull(GodNames.operatorProblem("Zeus"), "a famous name is the operator's call");
        assertNull(GodNames.operatorProblem("diddy"));
        assertNotNull(GodNames.operatorProblem("two words"));
        assertNotNull(GodNames.operatorProblem("x1"));
    }
}
