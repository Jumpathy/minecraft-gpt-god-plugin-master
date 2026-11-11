package net.bigyous.gptgodmc.god.living;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

class BondsTest {
    static final long T = 1_700_000_000_000L;

    @Test
    void regardMovesClampsAndRemembersWhy() {
        Bonds b = new Bonds();
        assertEquals(0, b.score("vorthak"));
        assertEquals(-30, b.move("vorthak", "Vorthak", -30, "smote my faithful Steve", T));
        assertEquals("rival", Bonds.stance(-30));
        assertEquals(-100, b.move("Vorthak", "Vorthak", -500, "burned my temple", T + 1));
        assertEquals("burned my temple", b.find("vorthak").why);
        // A small change keeps the bigger reason.
        b.move("vorthak", "Vorthak", 2, "a kind word", T + 2);
        assertEquals("burned my temple", b.find("vorthak").why);
        assertEquals(3, b.find("vorthak").changes.size());
        for (int i = 0; i < 10; i++) b.move("vorthak", "Vorthak", 1, "x" + i, T + 3 + i);
        assertEquals(Bonds.CHANGES_KEPT, b.find("vorthak").changes.size());
    }

    @Test
    void stancesCoverTheRange() {
        assertEquals("sworn enemy", Bonds.stance(-100));
        assertEquals("wary", Bonds.stance(-10));
        assertEquals("neutral", Bonds.stance(0));
        assertEquals("cordial", Bonds.stance(10));
        assertEquals("friend", Bonds.stance(40));
        assertEquals("ally", Bonds.stance(100));
    }

    @Test
    void presetsForTheOperator() {
        assertEquals(80, Bonds.preset("allies"));
        assertEquals(40, Bonds.preset("Friends"));
        assertEquals(0, Bonds.preset("neutral"));
        assertEquals(-40, Bonds.preset("rivals"));
        assertEquals(-80, Bonds.preset("enemies"));
        assertNull(Bonds.preset("+20"));
    }

    @Test
    void witnessedActsMoveRegard() {
        // A rival smiting my faithful angers me; smiting someone I despise pleases me a little.
        assertTrue(Bonds.witnessed("smite", null, false, "Steve", 6).delta() <= -10);
        assertTrue(Bonds.witnessed("smite", null, false, "Grief", -7).delta() > 0);
        assertEquals(0, Bonds.witnessed("smite", null, false, "Nobody", 0).delta());
        // Blessing my faithful pleases me; blessing those I despise does not.
        assertTrue(Bonds.witnessed("heal", null, false, "Steve", 5).delta() > 0);
        assertTrue(Bonds.witnessed("give_item", null, false, "Grief", -6).delta() < 0);
        // Words: insults and praise, only when they name me.
        assertTrue(Bonds.witnessed("announce", "Zel is a feeble pretender", true, null, 0).delta() < 0);
        assertTrue(Bonds.witnessed("commune", "I respect you, sister", true, null, 0).delta() > 0);
        assertEquals(0, Bonds.witnessed("announce", "Zel is a feeble pretender", false, null, 0).delta());
        // Private and harmless acts do nothing.
        assertEquals(0, Bonds.witnessed("look", null, false, "Steve", 9).delta());
    }

    @Test
    void contestedDevotion() {
        assertEquals(-1, Bonds.contested("Steve", 6, 5).delta());
        assertEquals(0, Bonds.contested("Steve", 6, 1).delta());
    }

    @Test
    void pactsAndOffers() {
        Bonds b = new Bonds();
        b.offer("zel", "Zel", Bonds.Pact.ALLIANCE, T);
        assertTrue(b.line("zel", "Zel", T).contains("offers you an alliance"));
        b.setPact("zel", "Zel", Bonds.Pact.TRUCE, T + 60_000, T);
        assertEquals(Bonds.Pact.TRUCE, b.find("zel").pact(T + 1));
        assertNull(b.find("zel").offered);
        // A truce lapses by itself.
        assertEquals(Bonds.Pact.NONE, b.find("zel").pact(T + 60_000));
        b.setPact("zel", "Zel", Bonds.Pact.WAR, 0, T);
        assertTrue(b.line("zel", "Zel", T).contains("holy war"));
    }

    @Test
    void survivesJson() {
        Bonds b = new Bonds();
        b.move("zel", "Zel", 25, "blessed my faithful", T);
        b.setPact("zel", "Zel", Bonds.Pact.ALLIANCE, 0, T);
        Bonds back = new Gson().fromJson(new Gson().toJson(b), Bonds.class);
        assertEquals(25, back.score("zel"));
        assertEquals(Bonds.Pact.ALLIANCE, back.find("zel").pact(T));
        assertTrue(back.line("zel", "Zel", T).startsWith("- Zel: friend (25), your ally by pact"));
    }
}
