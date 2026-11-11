package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.Pondering.Plan;
import net.bigyous.gptgodmc.god.vessel.Pondering.Why;

/** While it thinks, the god's body works: differently each time, and fitting the moment and its nature. */
class PonderingTest {
    static final Map<String, Integer> EVEN = Map.of();
    static final Map<String, Integer> FURIOUS = Map.of("wrath", 10, "pride", 9, "patience", 0, "humor", 0, "mercy", 0);
    static final Map<String, Integer> GENTLE = Map.of("wrath", 0, "pride", 1, "patience", 10, "humor", 8, "mercy", 10);

    @Test
    void everyPoseIsAWellFormedAnimation() {
        for (Pondering.Act a : Pondering.acts()) {
            assertTrue(a.anim().warnings().isEmpty(), a.id() + ": " + a.anim().warnings());
            assertTrue(a.anim().length() >= 10, a.id() + " is too short");
        }
        for (String id : Pondering.openings()) assertFalse(Pondering.act(id).anim().loop(), id + " plays once");
        for (String id : Pondering.workings()) assertTrue(Pondering.act(id).anim().loop(), id + " is held");
        for (String id : Pondering.deeps()) assertTrue(Pondering.act(id).anim().loop(), id + " is held");
        assertTrue(Pondering.workings().size() >= 10 && Pondering.openings().size() >= 8);
    }

    @Test
    void whatWokeItIsReadFromTheTrigger() {
        assertEquals(Why.STRUCK, Why.of("SplitBadger1038 STRUCK your vessel with stone_sword!"));
        assertEquals(Why.WOUNDED, Why.of("Jumpathy WOUNDED you with the Godslayer (health 70/100)"));
        assertEquals(Why.COMMAND, Why.of("the server operator gave you binding command #3"));
        assertEquals(Why.COMMAND, Why.of("binding command #3 from the operator is not yet done"));
        assertEquals(Why.PRAYER, Why.of("Ashwyn prays to you: \"I fell in lava, help\""));
        assertEquals(Why.SPOKEN, Why.of("Jumpathy spoke; SplitBadger1038 spoke"));
        assertEquals(Why.GODS, Why.of("another god: Thyvoru spoke to you"));
        assertEquals(Why.DANGER, Why.of("Pip is in danger they cannot see: creeper 4 blocks behind them"));
        assertEquals(Why.DEATH, Why.of("Pip was slain by Zombie"));
        assertEquals(Why.DEATH, Why.of("Pip fell from a high place"));
        assertEquals(Why.QUIET, Why.of("a quiet moment; nothing demands you, so carry on with your pastime"));
        assertEquals(Why.WATCH, Why.of("your regular watch over the world"));
        assertEquals(Why.OTHER, Why.of("you have returned from death"));
        assertEquals(Why.OTHER, Why.of(null));
    }

    @Test
    void itIsNeverAlwaysTheSame() {
        Set<String> openings = new HashSet<>(), workings = new HashSet<>(), motifs = new HashSet<>();
        for (long seed = 0; seed < 300; seed++) {
            Plan p = Pondering.choose(Why.STRUCK, VesselMood.CALM, EVEN, false, List.of(), List.of(), seed);
            openings.add(p.opening() == null ? "" : p.opening().id());
            workings.add(p.working().id());
            motifs.add(p.motif());
            assertNotNull(p.deep());
            assertTrue(Pondering.MOTIFS.contains(p.motif()) && Pondering.MOTIFS.contains(p.second()));
            assertFalse(p.motif().equals(p.second()), "the second motif adds something new");
        }
        assertTrue(openings.size() >= 4, "struck: " + openings);
        assertTrue(openings.contains("") && openings.contains("flinch_rise"), "sometimes it rises, sometimes not");
        assertTrue(workings.size() >= 4, workings.toString());
        assertTrue(motifs.size() >= 3, motifs.toString());
    }

    @Test
    void theMomentAndItsNatureShapeIt() {
        Map<String, Integer> furious = new HashMap<>(), gentle = new HashMap<>();
        int quietOpenings = 0;
        for (long seed = 0; seed < 600; seed++) {
            String f = Pondering.choose(Why.STRUCK, VesselMood.WRATHFUL, FURIOUS, false, List.of(), List.of(), seed)
                    .working().id();
            String g = Pondering.choose(Why.STRUCK, VesselMood.CALM, GENTLE, false, List.of(), List.of(), seed)
                    .working().id();
            furious.merge(f, 1, Integer::sum);
            gentle.merge(g, 1, Integer::sum);
            Plan idle = Pondering.choose(Why.WATCH, VesselMood.CALM, EVEN, false, List.of(), List.of(), seed);
            if (idle.opening() != null) quietOpenings++;
            assertEquals(80, idle.workAt(), "an idle look only shows a working if it runs long");
        }
        assertTrue(furious.getOrDefault("seethe", 0) > gentle.getOrDefault("seethe", 0) * 3,
                "a wrathful god seethes: " + furious + " vs " + gentle);
        assertTrue(gentle.getOrDefault("brood", 0) + gentle.getOrDefault("scales", 0)
                > furious.getOrDefault("brood", 0) + furious.getOrDefault("scales", 0), furious + " vs " + gentle);
        assertEquals(0, quietOpenings, "nothing startles it on its own watch");
        Plan cmd = Pondering.choose(Why.COMMAND, VesselMood.CALM, EVEN, true, List.of(), List.of(), 1);
        assertEquals(30, cmd.workAt(), "a command sets it working sooner");
        assertEquals(0, cmd.stage(0));
        assertEquals(1, cmd.stage(cmd.workAt()));
        assertEquals(2, cmd.stage(cmd.swellAt()));
        assertEquals(3, cmd.stage(cmd.deepAt() + 100));
    }

    @Test
    void whatItJustDidIsRarelyRepeated() {
        int fresh = 0, repeated = 0;
        for (long seed = 0; seed < 400; seed++) {
            if (Pondering.choose(Why.COMMAND, VesselMood.CALM, EVEN, true, List.of(), List.of(), seed).working().id()
                    .equals("weave")) {
                fresh++;
            }
            if (Pondering.choose(Why.COMMAND, VesselMood.CALM, EVEN, true, List.of("weave"), List.of(), seed)
                    .working().id().equals("weave")) {
                repeated++;
            }
        }
        assertTrue(fresh > 60 && repeated < fresh / 3, fresh + " then " + repeated);
    }

    @Test
    void posesTheGodWroteItselfJoinIn() {
        Choreo.Anim own = Choreo.fromText("think_spiral", """
                loop
                t=0 larm=90,40 rarm=90,40 head=0,-10
                t=1 larm=120,60 rarm=60,20 head=10,-10
                t=2 larm=90,40 rarm=90,40 head=0,-10
                """);
        Choreo.Anim once = Choreo.fromText("ponder_bow", "once\nt=0 torso=0\nt=1 torso=40");
        assertTrue(Pondering.isOwn("think_spiral") && Pondering.isOwn("Ponder_bow") && !Pondering.isOwn("dance"));
        int used = 0;
        for (long seed = 0; seed < 200; seed++) {
            Plan p = Pondering.choose(Why.PRAYER, VesselMood.CALM, EVEN, false, List.of(), List.of(own, once), seed);
            if (p.working().id().equals("own:think_spiral")) used++;
            assertFalse(p.working().id().equals("own:ponder_bow"), "a pose held in thought must loop");
        }
        assertTrue(used > 20, "its own pose is used often: " + used);
    }

    @Test
    void motifsStayNearTheBodyAndWithinBudget() {
        for (String m : Pondering.MOTIFS) {
            int drawn = 0;
            for (int k = 1; k <= 3; k++) {
                for (long tick = 0; tick < 400; tick += 2) {
                    List<Pondering.Dot> dots = Pondering.motif(m, tick, k, 42);
                    assertTrue(dots.size() <= Pondering.MAX_DOTS, m + " drew " + dots.size());
                    if (!dots.isEmpty()) drawn++;
                    for (Pondering.Dot d : dots) {
                        assertTrue(Float.isFinite(d.x()) && Float.isFinite(d.y()) && Float.isFinite(d.z()), m);
                        assertTrue(Math.abs(d.x()) <= 1.6f && Math.abs(d.z()) <= 1.6f, m + " strays: " + d);
                        assertTrue(d.y() >= -1.0f && d.y() <= 1.8f, m + " too high or low: " + d);
                    }
                    assertEquals(dots, Pondering.motif(m, tick, k, 42), m + " is not steady");
                }
            }
            assertTrue(drawn > 500, m + " is blank too often: " + drawn);
            assertTrue(Pondering.motif(m, 40, 3, 42).size() >= Pondering.motif(m, 40, 1, 42).size(), m + " swells");
        }
        assertTrue(Pondering.motif("nothing", 0, 1, 1).isEmpty());
        assertNull(Pondering.act("nothing"));
    }
}
