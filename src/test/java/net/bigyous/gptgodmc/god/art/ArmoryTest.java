package net.bigyous.gptgodmc.god.art;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.bigyous.gptgodmc.god.vessel.Choreo;
import net.bigyous.gptgodmc.god.vessel.VesselHeld;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Bone;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Frame;

/** The god's armory, its weapon animations, a relic in its hand, and the weight of its blows. */
class ArmoryTest {
    @AfterEach
    void forget() {
        Relics.load(null);
        Armory.load(null);
    }

    @Test
    void weaponAnimationsLandTheirBlow() {
        for (String style : Choreo.WEAPON_STYLES) {
            Choreo.Anim a = Choreo.preset(style);
            assertNotNull(a, style);
            assertTrue(a.warnings().isEmpty(), style + ": " + a.warnings());
            assertTrue(Choreo.strikes(a), style + " has a hit cue");
            for (Choreo.Cue c : a.cues()) {
                if (c.hit()) assertTrue(c.tick() > 4 && c.tick() < a.length(), style + " lands mid-swing");
            }
        }
        assertFalse(Choreo.strikes(Choreo.preset("draw_weapon")), "drawing is not a blow");
        assertFalse(Choreo.strikes(Choreo.preset("dance")));
        // A god writes its own: a cue that says only "hit", or hit with a sound; it survives saving.
        Choreo.Anim mine = Choreo.parse("two_cuts", List.of("t=0 rarm=40,10", "t=0.4 rarm=95,80", "t=0.6 rarm=95,-40",
                "t=1 rarm=95,80", "t=1.4 rarm=40,10"), List.of("t=0.6 hit", "t=1 hit sound=entity.player.attack.sweep"),
                false, null);
        assertEquals(2, mine.cues().stream().filter(Choreo.Cue::hit).count());
        Choreo.Anim again = Choreo.fromText("two_cuts", mine.text());
        assertEquals(mine.cues(), again.cues(), "hit cues are kept when saved");
        assertTrue(mine.text().contains("t=0.6 hit"), mine.text());
        assertFalse(Choreo.parse("x", List.of("t=0 rarm=1,1", "t=1 rarm=2,2"), List.of("t=0.5 hit=no"), false, null)
                .cues().stream().anyMatch(Choreo.Cue::hit));
    }

    @Test
    void aHeldRelicSitsInTheFistAndPointsAhead() {
        Art blade = SpritePainter.paint(Samples.BLADE.sprite(), false);
        float[] ends = Armory.ends(blade, true);
        assertTrue(ends[0] < 0.3 && ends[1] > 0.7, "gripped at the lower left: " + ends[0] + "," + ends[1]);
        assertTrue(ends[2] > 0.7 && ends[3] < 0.3, "points to the upper right: " + ends[2] + "," + ends[3]);
        float[] amulet = Armory.ends(SpritePainter.paint(Samples.AMULET.sprite(), false), false);
        assertTrue(amulet[1] > 0.8 && amulet[3] < 0.2, "a trinket is held at its foot and points up");

        VesselHeld.Weapon w = new VesselHeld.Weapon("x", "X", null, null, 15f, ends[0], ends[1], ends[2], ends[3], 0xFFFFFF);
        float unit = VesselModel.unit(1.2);
        for (String style : List.of("slash", "overhead", "thrust")) {
            Choreo.Anim a = Choreo.preset(style);
            for (int tick = 0; tick <= a.length(); tick += 7) {
                Frame arm = VesselModel.frames(Choreo.pose(a, tick)).get(Bone.RIGHT_ARM);
                Transformation t = VesselHeld.transform(w, arm, unit);
                // The picture's grip goes where the fist is, its point where the weapon points.
                Vector3f grip = place(t, new Vector3f(ends[0] - 0.5f, 0.5f - ends[1], 0));
                Vector3f tip = place(t, new Vector3f(ends[2] - 0.5f, 0.5f - ends[3], 0));
                Vector3f fist = VesselHeld.gripPx(arm).mul(unit), point = VesselHeld.pointPx(w, arm).mul(unit);
                assertTrue(grip.distance(fist) < 1e-3, style + " grip at " + tick + ": " + grip + " vs " + fist);
                assertTrue(tip.distance(point) < 1e-3, style + " point at " + tick + ": " + tip + " vs " + point);
                assertEquals(15f * unit, tip.distance(grip), 1e-3, "as long as asked");
            }
        }
        // Held ready, the blade points ahead of the body and a little down.
        Frame rest = VesselModel.frames(VesselModel.pose("idle", 0, 0, 0, null, null)).get(Bone.RIGHT_ARM);
        Vector3f ahead = VesselHeld.pointPx(w, rest).sub(VesselHeld.gripPx(rest));
        assertTrue(ahead.z > 8 && ahead.y < 0, "ready: " + ahead);
    }

    /** Where a point of the item's picture (centered, blocks) ends up under a display transformation. */
    private static Vector3f place(Transformation t, Vector3f p) {
        Vector3f v = new Quaternionf(t.getRightRotation()).transform(new Vector3f(p)).mul(t.getScale());
        return new Quaternionf(t.getLeftRotation()).transform(v).add(t.getTranslation());
    }

    @Test
    void aGodKeepsOnlyWhatItMade(@TempDir Path dir) {
        Relics.load(dir);
        Armory.load(dir.resolve("armory.json"));
        Relic mine = Relics.add("main", "Vaelor", "Gravecall", "staff", "A reaper's scythe of bone.", Samples.STAFF.sprite(), 1);
        Relic theirs = Relics.add("other", "Zel", "Tidebrand", "blade", "", Samples.BLADE.sprite(), 2);
        Relic sample = Relics.add("samples", "the first artist", "Skysplinter", "blade", "", Samples.KINDLED.sprite(), 3);
        assertNull(Armory.keep("main", mine));
        assertNull(Armory.keep("main", mine), "keeping twice is fine");
        assertNull(Armory.keep("main", sample), "the examples may be kept");
        assertTrue(Armory.keep("main", theirs).contains("Zel"));
        assertEquals(List.of(mine, sample), Armory.of("main"));
        assertTrue(Armory.describe("main").contains("Gravecall (staff)") && Armory.describe("main").contains("two forms"));
        assertEquals("nothing yet", Armory.describe("other"));
        // Kept across a restart; unmade relics fall out.
        Armory.load(dir.resolve("armory.json"));
        assertTrue(Armory.has("main", "gravecall"));
        Relics.remove(sample.id());
        assertEquals(List.of(mine), Armory.of("main"));
        assertTrue(Armory.drop("main", "gravecall"));
        assertFalse(Armory.drop("main", "gravecall"));
        assertEquals(org.bukkit.Material.NETHERITE_HOE, Armory.plain(mine), "a scythe stands in as a hoe");
        assertEquals(org.bukkit.Material.NETHERITE_SWORD, Armory.plain(theirs));
        for (int i = 0; i < Armory.MAX; i++) {
            Relic r = Relics.add("main", "Vaelor", "Blade " + i, "blade", "", Samples.BLADE.sprite(), 10 + i);
            String no = Armory.keep("main", r);
            if (i < Armory.MAX) assertNull(no);
        }
        assertTrue(Armory.keep("main", mine).contains("holds " + Armory.MAX));
    }

    @Test
    void aBlowWeighsWhatTheRelicIs() {
        assertEquals(7, Strikes.base("blade", Double.NaN));
        assertEquals(10, Strikes.base("blade", 10));
        assertEquals(7 * 1.3, Strikes.blow(7, "overhead", 1.5, 0), 1e-9, "a lordly body swings as written");
        assertTrue(Strikes.blow(7, "slash", 6, 0) > Strikes.blow(7, "slash", 1.5, 0), "a colossus hits harder");
        assertEquals(Strikes.MAX_BLOW, Strikes.blow(14, "overhead", 6, 20), 1e-9, "but never past the cap");
        assertTrue(Strikes.blow(7, "slash", 0.5, 0) >= 7 * 0.8, "a small body still cuts");
        assertTrue(Strikes.styleFactor("reap") > Strikes.styleFactor("slash"));
        Location a = new Location(null, 0, 0, 0), b = new Location(null, 10, 0, 0);
        assertEquals(3, Strikes.toSegment(new Location(null, 5, 3, 0), a, b), 1e-9);
        assertEquals(2, Strikes.toSegment(new Location(null, 12, 0, 0), a, b), 1e-9);
    }
}
