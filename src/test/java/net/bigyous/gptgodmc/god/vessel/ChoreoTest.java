package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.Choreo.Ch;

class ChoreoTest {
    @Test
    void parsesKeysAndAliases() {
        Choreo.Anim a = Choreo.parse("Deep Bow!", List.of("t=0 torso=0", "t=1 torso=50 head=10,20 right_arm=90,-20,15"),
                null, false, null);
        assertEquals("deep_bow", a.name());
        assertEquals(2, a.keys().size());
        assertEquals(20, a.length());
        Choreo.Key k = a.keys().get(1);
        assertEquals(50, k.get(Ch.TORSO), 1e-6);
        assertEquals(10, k.get(Ch.HEAD_YAW), 1e-6);
        assertEquals(20, k.get(Ch.HEAD_PITCH), 1e-6);
        assertEquals(15, k.get(Ch.RARM_TWIST), 1e-6);
        assertFalse(k.has(Ch.LARM_FWD));
        assertTrue(a.warnings().isEmpty(), a.warnings().toString());
    }

    @Test
    void clampsAnglesTimesAndCounts() {
        List<String> keys = new ArrayList<>();
        keys.add("t=0 torso=500 head=-300,0 rise=9");
        keys.add("t=99 larm=1000,1000");
        for (int i = 1; i <= 30; i++) keys.add("t=" + (i * 0.1) + " cape=10");
        Choreo.Anim a = Choreo.parse("x", keys, null, false, null);
        assertTrue(a.keys().size() <= Choreo.MAX_KEYS);
        assertTrue(a.length() <= Choreo.MAX_TICKS);
        Choreo.Key first = a.keys().get(0);
        assertEquals(Ch.TORSO.max, first.get(Ch.TORSO), 1e-6);
        assertEquals(Ch.HEAD_YAW.min, first.get(Ch.HEAD_YAW), 1e-6);
        assertEquals(Ch.RISE.max, first.get(Ch.RISE), 1e-6);
        assertFalse(a.warnings().isEmpty());
    }

    @Test
    void rejectsEmptyAndWarnsOnUnknown() {
        assertThrows(Choreo.Invalid.class, () -> Choreo.parse("x", List.of(), null, false, null));
        assertThrows(Choreo.Invalid.class, () -> Choreo.parse("x", List.of("t=0 tail=4"), null, false, null));
        Choreo.Anim a = Choreo.parse("x", List.of("t=0 torso=10 wing=4 ease=wobble"), null, false, null);
        assertEquals(2, a.warnings().size(), a.warnings().toString());
    }

    @Test
    void interpolatesSmoothlyBetweenKeys() {
        Choreo.Anim a = Choreo.parse("x", List.of("t=0 torso=0", "t=1 torso=40", "t=2 torso=40 ease=linear"), null,
                false, null);
        assertEquals(0, Choreo.sample(a, 0).values()[Ch.TORSO.ordinal()], 1e-4);
        assertEquals(20, Choreo.sample(a, 10).values()[Ch.TORSO.ordinal()], 1e-4); // smoothstep midpoint
        float early = Choreo.sample(a, 2).values()[Ch.TORSO.ordinal()];
        assertTrue(early < 40 * 2 / 20f, "eases in slowly: " + early);
        assertEquals(40, Choreo.sample(a, 20).values()[Ch.TORSO.ordinal()], 1e-4);
        assertEquals(40, Choreo.sample(a, 35).values()[Ch.TORSO.ordinal()], 1e-4);
        // Channels never set stay idle.
        assertTrue(Float.isNaN(Choreo.sample(a, 10).values()[Ch.HEAD_YAW.ordinal()]));
    }

    @Test
    void channelsBlendInFromIdleUntilTheirFirstKey() {
        Choreo.Anim a = Choreo.parse("x", List.of("t=0 torso=0", "t=1 head=30,0"), null, false, null);
        Choreo.Sample s = Choreo.sample(a, 10);
        assertEquals(30, s.values()[Ch.HEAD_YAW.ordinal()], 1e-4);
        assertEquals(0.5f, s.weights()[Ch.HEAD_YAW.ordinal()], 1e-4);
        assertEquals(1f, Choreo.sample(a, 20).weights()[Ch.HEAD_YAW.ordinal()], 1e-4);
    }

    @Test
    void loopsWrapAround() {
        Choreo.Anim a = Choreo.parse("x", List.of("t=0 torso=0", "t=1 torso=40", "t=2 torso=0"), null, true, null);
        assertEquals(40, a.length());
        float at10 = Choreo.sample(a, 10).values()[Ch.TORSO.ordinal()];
        float at50 = Choreo.sample(a, 50).values()[Ch.TORSO.ordinal()];
        assertEquals(at10, at50, 1e-4);
        // Continuous across the seam.
        float before = Choreo.sample(a, 39.9f).values()[Ch.TORSO.ordinal()];
        float after = Choreo.sample(a, 40.1f).values()[Ch.TORSO.ordinal()];
        assertTrue(Math.abs(before - after) < 1, before + " vs " + after);
    }

    @Test
    void weightBlendsInAndOut() {
        assertEquals(0, Choreo.weight(-1, 100), 1e-6);
        assertTrue(Choreo.weight(0, 100) > 0 && Choreo.weight(0, 100) < 1);
        assertEquals(1, Choreo.weight(50, 100), 1e-6);
        assertTrue(Choreo.weight(97, 100) < 0.5f);
        assertEquals(0, Choreo.weight(100, 100), 1e-6);
    }

    @Test
    void appliesOverIdleAndBlendsBack() {
        Choreo.Anim a = Choreo.parse("x", List.of("t=0 torso=60 rise=-0.36 body=0,90,0"), null, false, null);
        VesselModel.Pose idle = VesselModel.pose("idle", 0, 0, 0, null, null);
        VesselModel.Pose p = VesselModel.pose("idle", 0, 0, 0, null, null);
        Choreo.apply(p, a, 0, 1);
        assertEquals(Math.toRadians(60), p.torsoPitch, 1e-4);
        assertEquals(Math.toRadians(90), p.bodyYaw, 1e-4);
        assertEquals(-0.36f * VesselModel.HEIGHT_PX, p.offset.y, 1e-3);
        VesselModel.Pose none = VesselModel.pose("idle", 0, 0, 0, null, null);
        Choreo.apply(none, a, 0, 0);
        assertEquals(idle.torsoPitch, none.torsoPitch, 1e-6);
        VesselModel.Pose half = VesselModel.pose("idle", 0, 0, 0, null, null);
        Choreo.apply(half, a, 0, 0.5f);
        assertTrue(half.torsoPitch > idle.torsoPitch && half.torsoPitch < p.torsoPitch);
    }

    @Test
    void cuesParseAndFireOnce() {
        Choreo.Anim a = Choreo.parse("x", List.of("t=0 torso=0", "t=2 torso=10"),
                List.of("t=1 fx=heart at=hands n=99 sound=minecraft:entity.player.levelup pitch=9",
                        "t=0.5 fx=not_a_particle", "t=5 fx=heart"),
                false, Set.of("heart", "flame"));
        assertEquals(2, a.cues().size());
        Choreo.Cue c = a.cues().get(0);
        assertEquals(20, c.tick());
        assertEquals("hands", c.at());
        assertEquals(40, c.count());
        assertEquals("entity.player.levelup", c.sound());
        assertEquals(2f, c.pitch(), 1e-6);
        assertEquals(a.length(), a.cues().get(1).tick()); // the late cue moved to the end
        assertEquals(1, Choreo.cuesBetween(a, 18, 20).size());
        assertEquals(0, Choreo.cuesBetween(a, 20, 22).size());
        Choreo.Anim loop = Choreo.parse("y", List.of("t=0 torso=0", "t=1 torso=10"), List.of("t=0.5 fx=heart"),
                true, null);
        assertEquals(3, Choreo.cuesBetween(loop, -1, 60).size());
    }

    @Test
    void textRoundTrips() {
        Choreo.Anim a = Choreo.parse("x", List.of("t=0 torso=12.5 larm=90,20,10", "t=1.5 head=5,-10 ease=linear"),
                List.of("t=1 fx=flame at=rhand n=4 sound=block.note_block.bell pitch=1.5"), true, null);
        Choreo.Anim b = Choreo.fromText("x", a.text());
        assertEquals(a.text(), b.text());
        assertEquals(a.length(), b.length());
        assertTrue(b.loop());
    }

    @Test
    void everyPresetIsClean() {
        for (String name : Choreo.PRESETS.keySet()) {
            Choreo.Anim a = Choreo.preset(name);
            assertNotNull(a, name);
            assertTrue(a.warnings().isEmpty(), name + ": " + a.warnings());
            assertTrue(a.length() > 0 && a.length() <= Choreo.MAX_TICKS);
        }
    }
}
