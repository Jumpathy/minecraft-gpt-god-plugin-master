package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.AngelForms.Frame;
import net.bigyous.gptgodmc.god.vessel.AngelForms.Sprite;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Form;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Gaze;

class AngelFormsTest {

    // ---- Geometry ---------------------------------------------------------------------------------

    @Test
    void everyFormStaysInBudgetFiniteAndAboveTheGround() {
        for (Form form : Form.values()) {
            for (int tick = 0; tick < 1200; tick += 13) {
                for (double unfold : new double[] { 0, 0.3, 1 }) {
                    for (double eye : new double[] { 0, 0.6, 1 }) {
                        Frame f = AngelForms.frame(form, tick, unfold, eye, tick * 0.02, 0.3);
                        assertFalse(f.motes().isEmpty(), form + " drew nothing");
                        assertTrue(f.motes().size() + f.fallback().size() <= TrueForm.BUDGET,
                                form + " over budget: " + (f.motes().size() + f.fallback().size()));
                        for (TrueForm.Mote m : concat(f.motes(), f.fallback())) {
                            assertTrue(Double.isFinite(m.x()) && Double.isFinite(m.y()) && Double.isFinite(m.z()));
                            assertTrue(m.y() > -form.rise + 0.2, form + " sinks into the ground: " + m.y());
                            assertTrue(Math.abs(m.x()) < 16 && Math.abs(m.z()) < 16, form + " too wide");
                        }
                        for (Sprite s : f.sprites()) {
                            assertTrue(Double.isFinite(s.x()) && Double.isFinite(s.sx()));
                            assertTrue(s.y() > -form.rise + 0.5, form + " sprite sinks into the ground");
                            double q = s.qx() * s.qx() + s.qy() * s.qy() + s.qz() * s.qz() + s.qw() * s.qw();
                            assertEquals(1, q, 1e-6, "rotations stay unit quaternions");
                            assertTrue(s.textured() != (s.block() != null), "a sprite is a texture or a block");
                        }
                    }
                }
            }
        }
    }

    @Test
    void spritesKeepTheirNumberAndKindFromFrameToFrame() {
        for (Form form : Form.values()) {
            List<Sprite> a = AngelForms.frame(form, 5, 0.1, 0, 0, 0).sprites();
            List<Sprite> b = AngelForms.frame(form, 777, 1, 1, 2, -0.4).sprites();
            assertEquals(a.size(), b.size(), form + " must keep one entity per sprite");
            assertEquals(AngelForms.spriteCount(form), a.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).textured(), b.get(i).textured());
                assertEquals(a.get(i).block(), b.get(i).block());
                assertEquals(a.get(i).fallback(), b.get(i).fallback());
            }
            assertTrue(a.size() < 170, form + " has too many display entities: " + a.size());
        }
        assertEquals(0, AngelForms.spriteCount(Form.HYPERCUBE), "the hypercube stays pure light");
    }

    @Test
    void theAngelsAreFullOfEyes() {
        assertTrue(count(Form.OPHANIM, "eye_open") >= 18);
        assertTrue(count(Form.PILLAR, "eye_open") >= 40);
        assertTrue(count(Form.EYE, "eye_void") >= 12, "a swarm of lesser eyes");
        assertEquals(3, count(Form.EYE, "iris_ring"), "its iris is three wheels");
        assertEquals(1, count(Form.EYE, "iris"));
        assertEquals(1, count(Form.EYE, "sclera"));
        assertEquals(4, count(Form.EYE, "pupil"), "it has more pupils than it should");
        assertEquals(78, count(Form.SERAPHIM, "feather"), "six wings, the great pair in two layers");
        // Shut, the eyes wear their shut texture.
        assertEquals(0, AngelForms.frame(Form.OPHANIM, 200, 1, 0, 0, 0).sprites().stream()
                .filter(s -> "eye_open".equals(s.key())).count());
    }

    @Test
    void everyTextureKeyAndFallbackExists() {
        for (Form form : Form.values()) {
            for (Sprite s : AngelForms.frame(form, 100, 1, 1, 0, 0).sprites()) {
                if (s.textured()) assertTrue(TrueFormTextures.KEYS.contains(s.key()), s.key());
                if (s.textured()) assertNotNull(TrueFormTextures.image(s.key()));
                if (s.box() != null) assertTrue(TrueFormTextures.BOXES.contains(s.box()), s.box());
            }
            for (Sprite s : AngelForms.frame(form, 100, 1, 0, 0, 0).sprites()) {
                if (s.textured()) assertTrue(TrueFormTextures.KEYS.contains(s.key()), s.key());
            }
        }
    }

    @Test
    void theGreatEyeFollowsWhereItLooks() {
        for (double yaw : new double[] { 0, 1.2, -2.5 }) {
            Sprite iris = AngelForms.frame(Form.EYE, 300, 1, 1, yaw, 0.2).sprites().stream()
                    .filter(s -> "iris".equals(s.key())).findFirst().orElseThrow();
            Vector3d want = AngelForms.look(yaw, 0.2);
            Vector3d at = new Vector3d(iris.x(), iris.y(), iris.z()).normalize();
            assertTrue(at.dot(want) > 0.99, "the iris sits toward the watched mortal");
            Vector3d facing = iris.rotation().transform(new Vector3d(0, 0, 1));
            assertTrue(facing.dot(want) > 0.99, "and faces them");
        }
        Sprite ball = AngelForms.frame(Form.EYE, 300, 1, 1, 0, 0).sprites().stream()
                .filter(s -> "sclera".equals(s.key())).findFirst().orElseThrow();
        assertTrue(ball.billboard(), "the eyeball turns its face to every viewer");
    }

    @Test
    void theSeraphsVeilingWingsPartWhenItsEyeOpens() {
        // The face is at about (0, 2, 0.35); shut, feathers cross in front of it, open they lift away.
        double shut = nearestFeatherToFace(0), open = nearestFeatherToFace(1);
        assertTrue(shut < 1.6, "veiled: " + shut);
        assertTrue(open > shut + 1.0, "unveiled: " + open);
    }

    private static double nearestFeatherToFace(double eye) {
        Vector3d face = new Vector3d(0, 3.2, 1.4);
        double best = 99;
        for (Sprite s : AngelForms.frame(Form.SERAPHIM, 400, 1, eye, 0, 0).sprites()) {
            if (!"feather".equals(s.key())) continue;
            best = Math.min(best, face.distance(s.x(), s.y(), s.z()));
        }
        return best;
    }

    @Test
    void theWatchersPupilsCloseOnWhomItFinds() {
        List<Sprite> searching = pupils(0), fixed = pupils(1);
        assertTrue(fixed.get(0).sy() < searching.get(0).sy() * 0.5, "the bar shuts to a line");
        Vector3d main = new Vector3d(fixed.get(0).x(), fixed.get(0).y(), fixed.get(0).z());
        for (int i = 1; i < 4; i++) {
            Sprite far = searching.get(i), near = fixed.get(i);
            Vector3d mainS = new Vector3d(searching.get(0).x(), searching.get(0).y(), searching.get(0).z());
            assertTrue(mainS.distance(far.x(), far.y(), far.z()) > 0.8, "searching, its other pupils wander");
            assertTrue(main.distance(near.x(), near.y(), near.z()) < 0.3, "fixed, they slide into the first");
        }
        // Its lesser eyes open one after another, not all at once.
        long half = AngelForms.frame(Form.EYE, 300, 1, 0.5, 0, 0).sprites().stream()
                .filter(s -> "eye_void".equals(s.key())).count();
        assertTrue(half > 0 && half < count(Form.EYE, "eye_void"), "some open before others: " + half);
    }

    private static List<Sprite> pupils(double focus) {
        return AngelForms.frame(Form.EYE, 300, 1, 1, 0.4, 0.1, focus).sprites().stream()
                .filter(s -> "pupil".equals(s.key())).toList();
    }

    @Test
    void theyTowerOverMortals() {
        for (Form form : Form.values()) {
            if (form == Form.HYPERCUBE) continue;
            double minX = 0, maxX = 0, maxY = 0;
            for (Sprite s : AngelForms.frame(form, 300, 1, 1, 0, 0).sprites()) {
                minX = Math.min(minX, s.x());
                maxX = Math.max(maxX, s.x());
                maxY = Math.max(maxY, s.y() + (s.block() != null ? s.sy() / 2 : 0));
            }
            assertTrue(maxX - minX > 9 || maxY > 20, form + " should be vast: " + (maxX - minX) + " wide");
            assertTrue(form.rise >= 12, form + " hangs high");
        }
    }

    @Test
    void wheelsTurnAndTheFormUnfoldsOutOfAPoint() {
        Sprite a = AngelForms.frame(Form.OPHANIM, 100, 1, 1, 0, 0).sprites().get(0);
        Sprite b = AngelForms.frame(Form.OPHANIM, 160, 1, 1, 0, 0).sprites().get(0);
        assertTrue(Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z()) > 0.3);
        Frame folded = AngelForms.frame(Form.SERAPHIM, 100, 0, 0, 0, 0);
        assertEquals(1, folded.motes().size());
        for (Sprite s : folded.sprites()) assertTrue(s.sx() < 0.05 && Math.abs(s.x()) < 0.05);
    }

    @Test
    void facingAndAlongPointTheRightWay() {
        Vector3d d = new Vector3d(0.3, -0.5, 0.8).normalize();
        Quaterniond f = AngelForms.facing(d);
        assertTrue(f.transform(new Vector3d(0, 0, 1)).dot(d) > 0.9999);
        assertTrue(f.transform(new Vector3d(0, 1, 0)).y > 0, "kept upright");
        Quaterniond a = AngelForms.along(d, new Vector3d(0, 0, 1));
        assertTrue(a.transform(new Vector3d(0, 1, 0)).dot(d) > 0.9999);
        for (int i = 0; i < 400; i++) {
            double b = AngelForms.blink(i, 7);
            assertTrue(b >= 0 && b <= 1);
        }
    }

    // ---- The gaze's modes and the smite --------------------------------------------------------

    @Test
    void gazeModesMapFromConfigAndLegacyLethal() {
        assertEquals(Gaze.SMITE, Gaze.fromConfig("smite", null));
        assertEquals(Gaze.BLIND, Gaze.fromConfig(" BLIND ", true));
        assertEquals(Gaze.KILL, Gaze.fromConfig(null, true));
        assertEquals(Gaze.BLIND, Gaze.fromConfig(null, false));
        assertEquals(Gaze.KILL, Gaze.fromConfig(null, null));
        assertEquals(Gaze.BLIND, Gaze.fromConfig("nonsense", false));
        assertNull(Gaze.parse("explode"));
    }

    @Test
    void theGodMayOnlyKillWhereTheServerAllows() {
        assertEquals(Gaze.KILL, Gaze.allowed(Gaze.KILL, Gaze.KILL));
        assertEquals(Gaze.SMITE, Gaze.allowed(Gaze.SMITE, Gaze.KILL));
        assertEquals(Gaze.SMITE, Gaze.allowed(Gaze.KILL, Gaze.SMITE));
        assertEquals(Gaze.BLIND, Gaze.allowed(Gaze.KILL, Gaze.BLIND));
        assertEquals(Gaze.SMITE, Gaze.allowed(Gaze.SMITE, Gaze.BLIND), "smiting never kills, so it is allowed");
        assertEquals(Gaze.BLIND, Gaze.allowed(null, Gaze.BLIND));
        // Seizing kills too (slowly), so it follows the same rule; either lethal setting allows both.
        assertEquals(Gaze.SEIZE, Gaze.parse("Seize"));
        assertTrue(Gaze.SEIZE.lethal() && Gaze.KILL.lethal() && !Gaze.SMITE.lethal() && !Gaze.BLIND.lethal());
        assertEquals(Gaze.SEIZE, Gaze.allowed(Gaze.SEIZE, Gaze.KILL));
        assertEquals(Gaze.KILL, Gaze.allowed(Gaze.KILL, Gaze.SEIZE));
        assertEquals(Gaze.SMITE, Gaze.allowed(Gaze.SEIZE, Gaze.SMITE));
        assertEquals(Gaze.SEIZE, Gaze.fromConfig("seize", false));
    }

    @Test
    void aSmiteTakesHalfAHeartAtMostAndNeverKills() {
        assertEquals(1.0, TrueForm.smiteDamage(20));
        assertEquals(0.5, TrueForm.smiteDamage(1.5), 1e-9);
        assertEquals(0, TrueForm.smiteDamage(1.0));
        assertEquals(0, TrueForm.smiteDamage(0.5));
        assertEquals(0, TrueForm.smiteDamage(Double.NaN));
        for (double h = 0.5; h <= 40; h += 0.25) {
            double d = TrueForm.smiteDamage(h);
            assertTrue(d <= TrueForm.SMITE_MAX_DAMAGE && d >= 0);
            assertTrue(h - d >= Math.min(h, TrueForm.SMITE_FLOOR), "never below half a heart");
        }
    }

    @Test
    void theGuardStopsAnyBlowThatWouldKill() {
        // A 50-block fall on 3 hearts: held at half a heart.
        assertTrue(TrueForm.guardNeeded(6, 47));
        assertEquals(1.0, TrueForm.guardedHealth(6, 47));
        // Already lower than the floor: stays where it is, never 0.
        assertEquals(0.5, TrueForm.guardedHealth(0.5, 3));
        // Blows that do not kill pass through untouched.
        assertFalse(TrueForm.guardNeeded(10, 4));
        assertEquals(6, TrueForm.guardedHealth(10, 4));
        assertEquals(10, TrueForm.guardedHealth(10, 0));
    }

    @Test
    void itBoomsTheLineThenOthers() {
        assertEquals("You dare impose upon me?", TrueForm.smiteLine(0));
        assertNotEquals(TrueForm.smiteLine(0), TrueForm.smiteLine(1));
        assertEquals(TrueForm.smiteLine(1), TrueForm.smiteLine(1 + TrueForm.SMITE_AGAIN.size()));
    }

    private static void assertNotEquals(Object a, Object b) {
        assertFalse(a.equals(b));
    }

    // ---- Choosing the form ---------------------------------------------------------------------

    @Test
    void formsAreChosenByRequestThenServerThenNature() {
        assertEquals(Form.OPHANIM, Form.choose("ophanim", "eye", "evil", 0.5));
        assertEquals(Form.EYE, Form.choose(null, "eye", "benevolent", 0.5));
        assertEquals(Form.EYE, Form.choose("auto", "auto", "evil", 0.5));
        assertEquals(Form.SERAPHIM, Form.choose(null, "auto", "benevolent", 0.5));
        assertNull(Form.parse("cherubim"), "dropped: it did not look right");
        assertEquals(Form.values()[0], Form.choose("random", "eye", "evil", 0));
        assertEquals(Form.values()[Form.values().length - 1], Form.choose(null, "random", "evil", 0.9999));
        assertNotNull(Form.choose(null, null, "someone's own archetype", 0.42));
        for (String a : List.of("benevolent", "chill", "trickster", "evil", "wrathful", "cosmic")) {
            assertNotNull(Form.forArchetype(a), a);
        }
    }

    // ---- The client mod's scenes -----------------------------------------------------------------

    @Test
    void everyFormHasScenesTheEffectLanguageAccepts() {
        for (Form form : Form.values()) {
            var aura = TrueFormScenes.aura(form);
            var opening = TrueFormScenes.opening(form);
            assertTrue(aura.size() >= 4 && aura.size() <= net.bigyous.gptgodmc.god.FxScenes.MAX_LAYERS, form.id);
            assertTrue(opening.size() <= net.bigyous.gptgodmc.god.FxScenes.MAX_LAYERS, form.id);
            assertEquals(TrueFormScenes.LOOP, net.bigyous.gptgodmc.god.FxScenes.length(aura), form + " aura loops");
            assertTrue(aura.toString().contains("\"bloom\""), form + " should glow");
            assertTrue(opening.toString().contains("\"shock\""), form + " bends the world when it opens");
        }
        assertTrue(TrueFormScenes.aura(Form.EYE).toString().contains("desaturate"), "the Watcher drains the world");
        assertFalse(TrueFormScenes.struck().isEmpty());
        var seized = TrueFormScenes.seized(3, 8, -12.5, 110, true);
        assertTrue(seized.toString().contains("[3.0,8.0,-12.5]"), "the tendril runs to the form's heart");
        assertTrue(net.bigyous.gptgodmc.god.FxScenes.length(seized) >= 110);
    }

    // ---- Pack ----------------------------------------------------------------------------------

    @Test
    void thePackHasAFlatDoubleSidedModelForEveryTexture() {
        Map<String, byte[]> files = TrueFormPack.files();
        for (String key : TrueFormTextures.KEYS) {
            String model = new String(files.get("assets/gptgodmc/models/item/trueform/" + key + ".json"),
                    StandardCharsets.UTF_8);
            assertTrue(model.contains("\"south\"") && model.contains("\"north\""), key);
            assertTrue(model.contains("gptgodmc:item/trueform/" + key));
            assertNotNull(files.get("assets/gptgodmc/items/trueform/" + key + ".json"));
            assertNotNull(files.get("assets/gptgodmc/textures/item/trueform/" + key + ".png"));
        }
        assertNotNull(files.get("assets/gptgodmc/textures/item/trueform/feather.png.mcmeta"), "the feather burns");
        for (String key : TrueFormTextures.BOXES) {
            String model = new String(files.get("assets/gptgodmc/models/item/trueform/" + key + ".json"),
                    StandardCharsets.UTF_8);
            for (String face : List.of("north", "south", "east", "west", "up", "down")) {
                assertTrue(model.contains("\"" + face + "\""), key + " " + face);
            }
            assertNotNull(files.get("assets/gptgodmc/textures/item/trueform/" + key + ".png.mcmeta"), key + " moves");
        }
        // Glows are soft: their edge is clear and their heart is not solid.
        BufferedImage glow = TrueFormTextures.image("glow");
        assertEquals(0, glow.getRGB(0, glow.getHeight() / 2) >>> 24);
        int heart = glow.getRGB(glow.getWidth() / 2, glow.getHeight() / 2) >>> 24;
        assertTrue(heart > 40 && heart < 160, "glow heart alpha " + heart);
        assertEquals("gptgodmc:trueform/eye_open", TrueFormPack.modelId("eye_open"));
        // Deterministic, so the pack's hash never changes between builds.
        assertTrue(java.util.Arrays.equals(TrueFormPack.files().get("assets/gptgodmc/textures/item/trueform/lid.png"),
                files.get("assets/gptgodmc/textures/item/trueform/lid.png")));
    }

    @Test
    void texturesHaveTransparentCornersAndOpaqueHearts() {
        for (String key : TrueFormTextures.KEYS) {
            if (key.startsWith("glow") || key.equals("iris_ring")) continue; // no solid heart
            BufferedImage img = TrueFormTextures.image(key);
            assertEquals(0, img.getRGB(0, 0) >>> 24, key + " corner should be clear");
            int cx = img.getWidth() / 2, cy = img.getHeight() / 2;
            if (key.equals("feather")) cy = img.getHeight() * 2 / 3;
            assertEquals(255, img.getRGB(cx, cy) >>> 24, key + " should be solid in the middle");
        }
    }

    // ---- Helpers -------------------------------------------------------------------------------

    private static long count(Form form, String key) {
        return AngelForms.frame(form, 300, 1, 1, 0, 0).sprites().stream().filter(s -> key.equals(s.key())).count();
    }

    private static List<TrueForm.Mote> concat(List<TrueForm.Mote> a, List<TrueForm.Mote> b) {
        java.util.ArrayList<TrueForm.Mote> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
