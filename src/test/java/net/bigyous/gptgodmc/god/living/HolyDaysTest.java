package net.bigyous.gptgodmc.god.living;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

class HolyDaysTest {
    static final long T = 1_700_000_000_000L;
    static final long MIN = 60_000L;

    static HolyDays.Request req(HolyDays.Kind kind, String title, List<String> counts, int lead, int minutes) {
        return new HolyDays.Request(kind, "main", "Zel", title, "Bring me your finest", counts, "diamonds",
                "my scorn", kind == HolyDays.Kind.RAID ? "Grief" : null, null,
                kind == HolyDays.Kind.WAR ? "vorthak" : null, kind == HolyDays.Kind.WAR ? "Vorthak" : null, true, lead,
                minutes);
    }

    @Test
    void festivalTimelineAndScoring() {
        HolyDays h = new HolyDays();
        HolyDays.Request r = req(HolyDays.Kind.FESTIVAL, "Festival of Embers", List.of("hostile_kills", "fish_caught"),
                2, 10);
        assertNull(h.refusal(r, 2, 4, T));
        HolyDays.Day d = h.proclaim(r, T);
        assertEquals("announced", d.status);
        assertTrue(HolyDays.countdown(d, T).startsWith("begins in 2:00"));
        assertTrue(h.bar(d, T).contains("Festival of Embers"));
        // Nothing counts before it begins.
        assertTrue(h.credit("Steve", "hostile_kills", 1, null).isEmpty());
        assertEquals(HolyDays.Step.STARTED, h.step(d, T + 2 * MIN));
        assertEquals(1, h.credit("Steve", "hostile_kills", 3, List.of("main")).size());
        h.credit("Alex", "fish_caught", 1, null);
        // Only chosen deeds count, and only for the gods of the mortal's world.
        assertTrue(h.credit("Steve", "blocks_placed", 1, null).isEmpty());
        assertTrue(h.credit("Steve", "hostile_kills", 1, List.of("elsewhere")).isEmpty());
        h.award(d, "Alex", 5);
        assertEquals("Alex", h.ranking(d).get(0).getKey());
        assertTrue(h.bar(d, T + 3 * MIN).contains("top: Alex 6"));
        assertEquals(HolyDays.Step.ENDED, h.step(d, T + 12 * MIN));
        assertEquals("ended", d.status);
        assertTrue(d.outcome.startsWith("top: Alex 6, Steve 3"), d.outcome);
        assertTrue(h.perceive("main", T + 13 * MIN).contains("you promised: diamonds"));
    }

    @Test
    void limitsAndValidation() {
        HolyDays h = new HolyDays();
        h.proclaim(req(HolyDays.Kind.TRIAL, "One", List.of(), 0, 5), T);
        h.proclaim(req(HolyDays.Kind.TRIAL, "Two", List.of(), 0, 5), T);
        assertNotNull(h.refusal(req(HolyDays.Kind.TRIAL, "Three", List.of(), 0, 5), 2, 4, T));
        HolyDays fresh = new HolyDays();
        assertNotNull(fresh.refusal(req(HolyDays.Kind.TRIAL, "Bad", List.of("flying"), 0, 5), 2, 4, T));
        assertNotNull(fresh.refusal(new HolyDays.Request(HolyDays.Kind.RAID, "main", "Zel", "Raid", "", List.of(), "",
                "", null, null, null, null, false, 1, 4), 2, 4, T));
        // One raid per mortal at a time.
        fresh.proclaim(req(HolyDays.Kind.RAID, "Wrath", List.of(), 1, 4), T);
        assertNotNull(fresh.refusal(req(HolyDays.Kind.RAID, "More wrath", List.of(), 1, 4), 3, 4, T));
    }

    @Test
    void raidsAndHuntsAddTheirOwnDeeds() {
        HolyDays h = new HolyDays();
        HolyDays.Day raid = h.proclaim(req(HolyDays.Kind.RAID, "Wrath", List.of(), 0, 4), T);
        assertTrue(raid.counts.contains("survive"));
        // Only the raided mortal can survive the raid.
        assertTrue(h.credit("Steve", "survive", 1, null).isEmpty());
        assertEquals(1, h.credit("Grief", "survive", 1, null).size());
        HolyDays.Day hunt = h.proclaim(req(HolyDays.Kind.HUNT, "Hunt", List.of(), 0, 10), T);
        assertTrue(hunt.counts.contains("quarry_kill"));
    }

    @Test
    void holyWarSidesAndWinner() {
        HolyDays h = new HolyDays();
        HolyDays.Day war = h.proclaim(req(HolyDays.Kind.WAR, "War of Ash", List.of("hostile_kills"), 0, 20), T);
        assertTrue(war.counts.contains("rival_kills"), "pvp adds rival kills");
        assertNull(h.join(war, "Steve", "main"));
        assertNull(h.join(war, "Alex", "Vorthak"));
        assertNotNull(h.join(war, "Alex", "nobody"));
        // Mortals without a side score nothing in a war.
        assertTrue(h.credit("Herobrine", "hostile_kills", 1, null).isEmpty());
        h.credit("Steve", "hostile_kills", 4, null);
        h.credit("Alex", "hostile_kills", 2, null);
        h.award(war, "side:vorthak", 5);
        assertEquals(4, h.sideTotal(war, "main"));
        assertEquals(7, h.sideTotal(war, "vorthak"));
        assertEquals("vorthak", h.warWinner(war, null));
        // A turncoat leaves its points behind.
        h.join(war, "Alex", "main");
        assertEquals(5, h.sideTotal(war, "vorthak"));
        assertEquals("main", h.warWinner(war, "vorthak"));
        assertEquals(war, h.warBetween("vorthak", "main"));
        h.end(war, "vorthak", T + MIN);
        assertTrue(war.outcome.startsWith("Vorthak yielded"), war.outcome);
        assertNull(h.warBetween("vorthak", "main"));
        assertTrue(h.perceive("vorthak", T + 2 * MIN).contains("proclaimed by Zel"));
    }

    @Test
    void cadenceReminds() {
        HolyDays h = new HolyDays();
        // A new god gets a first quiet stretch.
        assertFalse(h.due("main", 4, T));
        assertFalse(h.due("main", 4, T + 3 * 60 * MIN));
        assertTrue(h.due("main", 4, T + 4 * 60 * MIN));
        h.nudged("main", T + 4 * 60 * MIN);
        assertFalse(h.due("main", 4, T + 5 * 60 * MIN));
        assertFalse(h.due("main", 0, T + 50 * 60 * MIN));
        h.proclaim(req(HolyDays.Kind.FESTIVAL, "Feast", List.of(), 0, 10), T + 9 * 60 * MIN);
        assertFalse(h.due("main", 4, T + 9 * 60 * MIN + MIN));
    }

    @Test
    void endedDaysAreTrimmedAndSurviveJson() {
        HolyDays h = new HolyDays();
        for (int i = 0; i < 20; i++) {
            HolyDays.Day d = h.proclaim(req(HolyDays.Kind.TRIAL, "T" + i, List.of(), 0, 5), T);
            h.end(d, null, T + 1);
        }
        assertEquals(HolyDays.ENDED_KEPT, h.days.size());
        HolyDays back = new Gson().fromJson(new Gson().toJson(h), HolyDays.class);
        assertEquals(h.counter, back.counter);
        assertEquals("ended", back.days.get(0).status);
    }

    @Test
    void clockAndProgress() {
        assertEquals("0:05", HolyDays.clock(4_200));
        assertEquals("2 h 30 m", HolyDays.clock(150 * MIN));
        HolyDays h = new HolyDays();
        HolyDays.Day d = h.proclaim(req(HolyDays.Kind.OMEN, "Omen", List.of(), 10, 100), T);
        assertEquals(0.5f, HolyDays.progress(d, T + 5 * MIN), 1e-3);
        h.step(d, T + 10 * MIN);
        assertEquals(0.75f, HolyDays.progress(d, T + 35 * MIN), 1e-3);
        assertEquals(HolyDays.Kind.WAR, HolyDays.Kind.parse("holy_war"));
        assertEquals(HolyDays.Kind.FESTIVAL, HolyDays.Kind.parse("Feast"));
    }
}
