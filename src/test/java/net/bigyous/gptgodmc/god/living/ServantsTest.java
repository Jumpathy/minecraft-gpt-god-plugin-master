package net.bigyous.gptgodmc.god.living;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.entity.EntityType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ServantsTest {
    @Test
    void everyKindIsAVanillaMob() {
        for (ServantKind k : ServantKind.values()) {
            EntityType t = EntityType.valueOf(k.mob);
            assertNotNull(t.getEntityClass(), k.id);
            assertTrue(org.bukkit.entity.Mob.class.isAssignableFrom(t.getEntityClass()), k.id + " must be a mob");
            assertTrue(k.health > 0 && k.speed > 0, k.id);
        }
        assertEquals(ServantKind.ANGEL, ServantKind.parse("Seraph"));
        assertEquals(ServantKind.SHADE, ServantKind.parse("wraith"));
        assertEquals(ServantKind.Order.ATTACK, ServantKind.Order.parse("smite"));
    }

    @Test
    void limits() {
        assertNull(ServantRules.refusal(true, ServantKind.ANGEL, "greater", 0, 0, 3, 8));
        assertNotNull(ServantRules.refusal(false, ServantKind.ANGEL, "greater", 0, 0, 3, 8));
        assertNotNull(ServantRules.refusal(true, ServantKind.ANGEL, "greater", 3, 3, 3, 8));
        assertNotNull(ServantRules.refusal(true, ServantKind.ANGEL, "lesser", 1, 1, 3, 8), "lesser gods keep fewer");
        assertNotNull(ServantRules.refusal(true, ServantKind.ANGEL, "supreme", 0, 8, 3, 8));
        assertNotNull(ServantRules.refusal(true, null, "supreme", 0, 0, 3, 8));
        assertEquals(10, ServantRules.minutes(0, 30));
        assertEquals(30, ServantRules.minutes(999, 30));
        assertNull(ServantRules.attackNote("Grief", -6));
        assertTrue(ServantRules.attackNote("Steve", 7).contains("betrayal"));
    }

    @Test
    void servantsHoldUpAgainstTheWorldButNotAgainstMortals() {
        // Being steered into a wall, a pond or a cactus does no harm; what a mortal can bring to bear still does.
        for (String cause : List.of("SUFFOCATION", "CRAMMING", "FALL", "DROWNING", "CONTACT", "FLY_INTO_WALL")) {
            assertTrue(ServantRules.shrugsOff(cause), cause);
        }
        for (String cause : List.of("ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "PROJECTILE", "ENTITY_EXPLOSION",
                "BLOCK_EXPLOSION", "FIRE", "FIRE_TICK", "LAVA", "MAGIC", "VOID", "KILL", "LIGHTNING")) {
            assertTrue(!ServantRules.shrugsOff(cause), cause);
        }
        assertTrue(!ServantRules.shrugsOff(null));
        // Every name above is a real cause, so a renamed constant fails here and not silently in game.
        for (String cause : List.of("SUFFOCATION", "CRAMMING", "FALL", "DROWNING", "CONTACT", "FLY_INTO_WALL",
                "HOT_FLOOR")) {
            assertNotNull(org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf(cause));
        }

        // Creatures do not pick servants as prey, but may answer a blow.
        assertTrue(!ServantRules.mayHunt("CLOSEST_ENTITY"));
        assertTrue(!ServantRules.mayHunt("RANDOM_TARGET"));
        assertTrue(ServantRules.mayHunt("TARGET_ATTACKED_ENTITY"));
        assertTrue(ServantRules.mayHunt("TARGET_ATTACKED_NEARBY_ENTITY"));
        assertTrue(ServantRules.mayHunt("CUSTOM"));
        for (String reason : List.of("CLOSEST_ENTITY", "RANDOM_TARGET", "TARGET_ATTACKED_ENTITY")) {
            assertNotNull(org.bukkit.event.entity.EntityTargetEvent.TargetReason.valueOf(reason));
        }

        // A guard stands among monsters; the frail kinds stay frail.
        assertEquals(0.25, ServantRules.mobHarm(ServantKind.GUARD));
        assertEquals(1.0, ServantRules.mobHarm(ServantKind.IMP));
        assertEquals(1.0, ServantRules.mobHarm(ServantKind.PRIEST));
        for (ServantKind k : ServantKind.values()) {
            assertTrue(ServantRules.mobHarm(k) > 0 && ServantRules.mobHarm(k) <= 1, k.id + " can still be killed");
        }
        // An easy-difficulty zombie (2.5 a blow, once a second) needs over two minutes for a guard.
        assertTrue(ServantKind.GUARD.health / (2.5 * ServantRules.mobHarm(ServantKind.GUARD)) > 120);
    }

    @Test
    void aServantsDeathNamesWhatKilledIt() {
        assertEquals("slain by Zombie", ServantRules.death(ServantRules.creature("ZOMBIE"), "ENTITY_ATTACK"));
        assertEquals("slain by Wither Skeleton", ServantRules.death(ServantRules.creature("WITHER_SKELETON"), null));
        assertEquals("slain by Brakk", ServantRules.death("Brakk", "ENTITY_ATTACK"));
        assertEquals("slain by Creeper", ServantRules.death("Creeper", "ENTITY_EXPLOSION"));
        assertEquals("destroyed by an explosion", ServantRules.death(null, "BLOCK_EXPLOSION"));
        assertEquals("destroyed by an explosion", ServantRules.death(" ", "ENTITY_EXPLOSION"));
        assertEquals("crushed inside a wall", ServantRules.death(null, "SUFFOCATION"));
        assertEquals("unmade by a command (/kill)", ServantRules.death(null, "KILL"));
        assertEquals("destroyed by sonic boom", ServantRules.death(null, "SONIC_BOOM"));
        assertEquals("slain by something unseen", ServantRules.death(null, null));
        // No cause is ever told as the old "slain by something".
        for (var cause : org.bukkit.event.entity.EntityDamageEvent.DamageCause.values()) {
            String told = ServantRules.death(null, cause.name());
            assertTrue(!told.isBlank() && !told.contains("something") && !told.contains("_"), cause + ": " + told);
        }
    }

    @Test
    void rigsKeepTheirShapeAndOrder() {
        for (ServantKind k : ServantKind.values()) {
            List<ServantRig.Piece> a = ServantRig.pieces(k, 0, false), b = ServantRig.pieces(k, 37, true);
            assertEquals(a.size(), b.size(), k.id);
            for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).part(), b.get(i).part());
            assertTrue(a.size() <= 32, k.id + " stays light: " + a.size());
            if (k != ServantKind.PRIEST) assertTrue(a.size() >= 4, k.id + " is dressed");
        }
    }

    @Test
    void angelWingsAreWideBehindTheBackAndBeat() {
        List<ServantRig.Piece> up = ServantRig.pieces(ServantKind.ANGEL, ServantRig.beatTicks(ServantKind.ANGEL) / 4, true);
        List<ServantRig.Piece> down = ServantRig.pieces(ServantKind.ANGEL, 3 * ServantRig.beatTicks(ServantKind.ANGEL) / 4,
                true);
        float span = 0, upTip = 0, downTip = 0;
        for (int i = 0; i < up.size(); i++) {
            ServantRig.Piece p = up.get(i);
            if (!p.part().startsWith("wing")) continue;
            span = Math.max(span, Math.abs(p.center().x));
            assertTrue(p.center().z < 0.05f, "wings behind the back: " + p.center());
            upTip = Math.max(upTip, p.center().y);
            downTip = Math.max(downTip, down.get(i).center().y);
        }
        assertTrue(2 * span > 2.0f, "angel wing feathers (centers) span " + 2 * span);
        assertTrue(upTip - downTip > 0.3f, "the wingbeat moves: " + upTip + " vs " + downTip);
        // The halo floats above the angel's head.
        for (ServantRig.Piece p : up) {
            if (p.part().equals("halo")) assertTrue(p.center().y > ServantKind.ANGEL.height(), "halo " + p.center());
        }
    }

    @Test
    void paintedBodiesKeepTheirPiecesAndWearTheirAtlas() {
        for (ServantKind k : ServantKind.values()) {
            List<ServantBody.Part> still = ServantBody.parts(k, ServantBody.Motion.idle(0));
            List<ServantBody.Part> busy = ServantBody.parts(k, new ServantBody.Motion(37, true, 6));
            assertEquals(still.size(), busy.size(), k.id);
            assertTrue(still.size() >= 12 && still.size() <= 40, k.id + " has " + still.size() + " pieces");
            ServantSkin.Layout layout = ServantSkin.layout(k);
            java.util.Set<String> worn = new java.util.HashSet<>();
            for (int i = 0; i < still.size(); i++) {
                ServantBody.Part p = still.get(i);
                assertEquals(p.key(), busy.get(i).key(), k.id + " piece " + i);
                assertEquals(layout.region(p.key()).def().shape(), p.shape(), k.id + " " + p.key());
                assertTrue(p.center().y > -0.6f && p.center().y < ServantBody.top(k) + 0.3f, k.id + " " + p.key());
                worn.add(p.key());
            }
            assertEquals(layout.regions().keySet(), worn, k.id + " wears every piece it has a skin for");
            // The halberd, the sword, the scythe: a blow moves something.
            if (k != ServantKind.PRIEST) {
                boolean moved = false;
                List<ServantBody.Part> rest = ServantBody.parts(k, new ServantBody.Motion(37, true, -1));
                for (int i = 0; i < rest.size(); i++) moved |= rest.get(i).center().distance(busy.get(i).center()) > 0.05f;
                assertTrue(moved, k.id + " swings when it strikes");
            }
        }
        // The seraph's wings spread wide behind it and beat.
        float span = 0;
        for (ServantBody.Part p : ServantBody.parts(ServantKind.ANGEL, ServantBody.Motion.idle(4))) {
            if (!p.key().startsWith("wing_")) continue;
            span = Math.max(span, Math.abs(p.center().x) + p.size().x / 2);
            assertTrue(p.center().z < 0.1f, "wings behind the back: " + p.center());
        }
        assertTrue(span > 1.3f && span < 2.4f, "half span " + span);
    }

    @Test
    void atlasesArePackedAndPainted() {
        for (ServantKind k : ServantKind.values()) {
            ServantSkin.Layout layout = ServantSkin.layout(k);
            assertEquals(Integer.bitCount(layout.height()), 1, "power of two: " + layout.height());
            List<ServantSkin.Region> regions = List.copyOf(layout.regions().values());
            for (int i = 0; i < regions.size(); i++) {
                ServantSkin.Region a = regions.get(i);
                assertTrue(a.x() >= 0 && a.y() >= 0 && a.x() + a.width() <= layout.width()
                        && a.y() + a.height() <= layout.height(), k.id + " " + a.def().key() + " fits");
                for (int j = i + 1; j < regions.size(); j++) {
                    ServantSkin.Region b = regions.get(j);
                    boolean apart = a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x()
                            || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y();
                    assertTrue(apart, k.id + ": " + a.def().key() + " overlaps " + b.def().key());
                }
            }
            ServantTextures.Atlas atlas = ServantTextures.atlas(k);
            assertEquals(ServantSkin.FRAMES, atlas.frames().length);
            for (ServantSkin.Region r : regions) {
                int painted = 0;
                for (int y = r.y(); y < r.y() + r.height(); y++) {
                    for (int x = r.x(); x < r.x() + r.width(); x++) {
                        int argb = atlas.frames()[0].getRGB(x, y);
                        if ((argb >>> 24) == 0) continue;
                        painted++;
                        assertTrue((argb & 0xFFFFFF) != 0xFF00FF, k.id + " " + r.def().key() + " is not painted");
                    }
                }
                assertTrue(painted > 8, k.id + " " + r.def().key() + " has only " + painted + " texels");
            }
        }
    }

    @Test
    void packHoldsEveryPieceAndIsStable() {
        java.util.Map<String, byte[]> files = ServantPack.files();
        for (ServantKind k : ServantKind.values()) {
            String tex = "assets/gptgodmc/textures/item/servant/" + k.id + ".png";
            assertTrue(files.containsKey(tex), tex);
            assertTrue(files.containsKey(tex + ".mcmeta"), tex + ".mcmeta");
            for (ServantSkin.Def d : ServantSkin.defs(k)) {
                String path = "servant/" + k.id + "/" + d.key();
                assertEquals("gptgodmc:" + path, ServantPack.modelId(k, d.key()));
                assertTrue(files.containsKey("assets/gptgodmc/items/" + path + ".json"), path);
                String model = new String(files.get("assets/gptgodmc/models/item/" + path + ".json"),
                        java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(model.contains("gptgodmc:item/servant/" + k.id), path);
                // A box has six faces; a plane or glow shows its one picture from both sides.
                int faces = model.split("\\\"uv\\\"", -1).length - 1;
                assertEquals(d.shape() == ServantSkin.Shape.BOX ? 6 : 2, faces, path);
            }
        }
        java.util.Map<String, byte[]> again = ServantPack.files();
        for (var e : files.entrySet()) assertTrue(java.util.Arrays.equals(e.getValue(), again.get(e.getKey())), e.getKey());
        // They ride in the god's pack.
        assertTrue(net.bigyous.gptgodmc.god.vessel.VesselPack.files().keySet().containsAll(files.keySet()));
    }

    @Test
    void paintedPiecesTurnWithTheBody() {
        ServantBody.Part helm = null;
        for (ServantBody.Part p : ServantBody.parts(ServantKind.GUARD, ServantBody.Motion.idle(0))) {
            if (p.key().equals("shield")) helm = p;
        }
        assertNotNull(helm);
        Vector3f south = ServantBody.transform(helm, 0).getTranslation();
        Vector3f east = ServantBody.transform(helm, -90).getTranslation();
        assertTrue(south.z > 0.5f, "the shield is held before it: " + south);
        assertTrue(east.x > 0.5f, "and stays before it when it faces east: " + east);
    }

    @Test
    void transformsTurnWithTheBody() {
        ServantRig.Piece crest = ServantRig.pieces(ServantKind.GUARD, 0, false).get(1); // the plume, behind
        Vector3f south = ServantRig.transform(crest, 0).getTranslation();
        Vector3f east = ServantRig.transform(crest, -90).getTranslation();
        assertTrue(south.z < 0, "behind when facing south: " + south);
        assertTrue(east.x < 0, "behind when facing east: " + east);
        assertEquals(170, Servants.approach(160, 170, 25), 1e-4);
        assertEquals(185, Servants.approach(170, -170, 15), 1e-4);
    }
}
