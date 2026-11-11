package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.loggables.DivineActLoggable;

/** How mighty a god is, what other gods' acts do to its schedule, and what it spends. */
class PowerTest {

    @Test
    void powerDecidesWhichToolsAGodMayUse() {
        assertTrue(Power.SUPREME.allows("meteor"));
        assertFalse(Power.GREATER.allows("meteor"));
        assertTrue(Power.GREATER.allows("smite"));
        assertFalse(Power.LESSER.allows("smite"));
        assertTrue(Power.LESSER.allows("whisper"));
        assertTrue(Power.LESSER.allows("heal"));
        for (String t : Power.CATACLYSMS) assertTrue(ToolRegistry.get(t) != null, "no such tool: " + t);
        for (String t : Power.MIGHTY) assertTrue(ToolRegistry.get(t) != null, "no such tool: " + t);
    }

    @Test
    void omniscienceDecidesWhoOverhearsPrayers() {
        assertTrue(Power.SUPREME.overhears(-1), "a supreme god hears every prayer, body or not");
        assertTrue(Power.GREATER.overhears(20));
        assertFalse(Power.GREATER.overhears(200));
        assertFalse(Power.GREATER.overhears(-1), "formless, a greater god hears no one else's prayers");
        assertFalse(Power.LESSER.overhears(1));
    }

    @Test
    void powerNamesParse() {
        assertEquals(Power.SUPREME, Power.parse("Omniscient"));
        assertEquals(Power.LESSER, Power.parse("lesser"));
        assertNull(Power.parse("huge"));
        assertEquals("", Power.SUPREME.standing());
    }

    @Test
    void otherGodsActsWaitForTheNextLook() {
        EventLogger.reset();
        EventLogger.addLoggable(new DivineActLoggable("Vorthak used its power smite", false, null));
        assertFalse(EventLogger.hasPending(), "a passive event never makes the god look by itself");
        assertEquals(1, EventLogger.pendingCount());
        EventLogger.addLoggable(new DivineActLoggable("Vorthak named you", true, "Vorthak named you"));
        assertTrue(EventLogger.hasPending());
        assertTrue(God.divineOnly(DivineActLoggable.DIVINE + ": Vorthak named you"));
        assertFalse(God.divineOnly(DivineActLoggable.DIVINE + ": x; Steve prays to you"));
        EventLogger.reset();
    }

    @Test
    void spendingRollsOffAfterAnHour() {
        Spending.reset();
        long t = 1_000_000_000L;
        Spending.add("ysmere", 0.30, t);
        Spending.add("ysmere", 0.25, t + 60_000);
        assertEquals(0.55, Spending.lastHour("ysmere", t + 120_000), 1e-9);
        assertTrue(Spending.weary("ysmere", 0.50, t + 120_000));
        assertFalse(Spending.weary("ysmere", 0, t + 120_000), "0 means no limit");
        assertEquals(0.25, Spending.lastHour("ysmere", t + 3_630_000), 1e-9);
        assertEquals(0, Spending.lastHour("main", t), 1e-9);
        Spending.reset();
    }

    @Test
    void aWrathfulGodIsAngeredMoreByTheSameInsult() {
        Heart calm = new Heart();
        Heart wrathful = new Heart();
        long t = 1_700_000_000_000L;
        calm.moved("prayer", "K prays to you: \"you are weak\"", true, "K", 0, "you are weak", t, 5, 5, 0);
        wrathful.moved("prayer", "K prays to you: \"you are weak\"", true, "K", 0, "you are weak", t, 5, 5, 10);
        assertTrue(wrathful.level(Heart.Feeling.WRATH) > calm.level(Heart.Feeling.WRATH) * 2,
                calm.level(Heart.Feeling.WRATH) + " vs " + wrathful.level(Heart.Feeling.WRATH));
    }
}
