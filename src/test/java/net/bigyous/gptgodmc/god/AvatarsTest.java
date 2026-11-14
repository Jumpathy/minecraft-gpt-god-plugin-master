package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.Avatars.Bare;
import net.bigyous.gptgodmc.god.Avatars.Look;

/** The god sees how a mortal's skin looks: what they wear, or how little. */
class AvatarsTest {
    static final int LIGHT = 0xE0AC8C, DEEP = 0x5C3A28, OLIVE = 0xB98A64;
    static final int BROWN_HAIR = 0x4A2E18, BLACK = 0x161616, CYAN = 0x00A8A8, BLUE = 0x3030A0, GREY = 0x6E6E6E;

    static Look read(SkinPainter s) {
        return Avatars.read(s.img, false);
    }

    @Test
    void aClothedMortalIsDescribedByWhatTheyWear() {
        Look l = read(SkinPainter.person(LIGHT).hair(BROWN_HAIR).shirt(CYAN).trousers(BLUE).shoes(GREY));
        assertEquals(Bare.NONE, l.bare());
        String s = Avatars.phrase(l, false, false);
        assertEquals("dark brown hair, a cyan top, blue trousers", s);
        assertFalse(Avatars.exposed(l, false, false));
    }

    @Test
    void nakedSkinIsSeenWhateverItsTone() {
        for (int tone : new int[] { LIGHT, OLIVE, DEEP }) {
            Look l = read(SkinPainter.person(tone).hair(BLACK));
            assertEquals(Bare.NAKED, l.bare(), Integer.toHexString(tone));
            String s = Avatars.phrase(l, false, false);
            assertTrue(s.startsWith("black hair, NAKED"), s);
            assertTrue(Avatars.exposed(l, false, false));
            assertFalse(s.contains("skin tone") || s.contains("pale") || s.contains(" tan"),
                    "the colour of their skin is never named: " + s);
        }
    }

    @Test
    void armorCoversWhatTheSkinDoesNot() {
        Look naked = read(SkinPainter.person(LIGHT).hair(BLACK));
        assertEquals("black hair, naked under their armor", Avatars.phrase(naked, true, true));
        assertFalse(Avatars.exposed(naked, true, true));
        assertTrue(Avatars.phrase(naked, true, false).contains("bare legs and hips below the armor"));
        assertFalse(Avatars.exposed(naked, true, false), "half dressed in iron is not walking about naked");
        Look shirtless = read(SkinPainter.person(LIGHT).hair(BLACK).trousers(BLUE));
        assertEquals("black hair, blue trousers", Avatars.phrase(shirtless, true, false));
    }

    @Test
    void halfDressedIsNotNaked() {
        Look under = read(SkinPainter.person(LIGHT).hair(BLACK).underwear(0xFFFFFF));
        assertEquals(Bare.UNDERWEAR, under.bare());
        assertTrue(Avatars.phrase(under, false, false).contains("nearly naked"));
        assertTrue(Avatars.exposed(under, false, false));
        Look shirtless = read(SkinPainter.person(DEEP).hair(BLACK).trousers(BLUE));
        assertEquals(Bare.CHEST, shirtless.bare());
        assertEquals("black hair, blue trousers, bare-chested", Avatars.phrase(shirtless, false, false));
        assertFalse(Avatars.exposed(shirtless, false, false));
        Look shorts = read(SkinPainter.person(LIGHT).hair(BLACK).shirt(CYAN).shorts(BLUE));
        assertEquals(Bare.NONE, shorts.bare());
        assertTrue(Avatars.phrase(shorts, false, false).contains("blue shorts or skirt"),
                Avatars.phrase(shorts, false, false));
    }

    @Test
    void theSecondLayerCoversTheFirst() {
        Look l = read(SkinPainter.person(LIGHT).hair(BLACK).jacket(0xA02020));
        assertTrue(l.bare() != Bare.NAKED && l.bare() != Bare.UNDERWEAR, l.toString());
        assertTrue(l.top().contains("red"), l.toString());
    }

    @Test
    void hoodsMasksAndCreatures() {
        Look hood = read(SkinPainter.person(LIGHT).hair(BLACK).shirt(CYAN).trousers(BLUE).hood(0x2E5E2E));
        assertTrue(hood.head().endsWith("hood"), hood.toString());
        assertNull(hood.hair());
        Look mask = read(SkinPainter.person(LIGHT).hair(BLACK).shirt(CYAN).trousers(BLUE).mask(GREY));
        assertTrue(Avatars.phrase(mask, false, false).startsWith("a grey mask or helmet over the face"),
                Avatars.phrase(mask, false, false));
        Look creeper = read(SkinPainter.person(0x4CAF50));
        assertEquals(Bare.NONE, creeper.bare(), "a creature's own colour is not bare skin");
        String c = Avatars.phrase(creeper, false, false);
        assertTrue(c.startsWith("an unhuman green face, a green body"), c);
        Look bald = read(SkinPainter.person(LIGHT).shirt(CYAN).trousers(BLUE));
        assertEquals("a bald head", bald.hair());
    }

    @Test
    void oldSkinsAndNoSkinAtAll() {
        Look old = Avatars.read(new SkinPainter(LIGHT, true).hair(BLACK).shirt(CYAN).trousers(BLUE).img, false);
        assertEquals("black hair, a cyan top, blue trousers", Avatars.phrase(old, false, false));
        Look oldNaked = Avatars.read(new SkinPainter(LIGHT, true).hair(BLACK).img, false);
        assertEquals(Bare.NAKED, oldNaked.bare());
        assertTrue(Avatars.read(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), true).defaultSkin());
        assertEquals("a default skin, no look of their own, a cape", Avatars.phrase(Avatars.defaultLook(true), false,
                false));
        Look caped = Avatars.read(SkinPainter.person(LIGHT).hair(BLACK).shirt(CYAN).trousers(BLUE).img, true);
        assertTrue(Avatars.phrase(caped, false, false).endsWith(", a cape"));
    }

    @Test
    void coloursHavePlainNames() {
        assertEquals("black", Avatars.colorName(0x101010));
        assertEquals("white", Avatars.colorName(0xF0F0F0));
        assertEquals("red", Avatars.colorName(0xC02020));
        assertEquals("dark blue", Avatars.colorName(0x101060));
        assertEquals("green", Avatars.colorName(0x30A030));
        assertEquals("pink", Avatars.colorName(0xF0A0B0));
        assertEquals("purple", Avatars.colorName(0x7030B0));
    }

    @Test
    void onlyMojangsTextureServerIsAsked() {
        assertTrue(AvatarWatch.allowed("http://textures.minecraft.net/texture/abc123"));
        assertFalse(AvatarWatch.allowed("http://evil.example/texture/abc123"));
        assertFalse(AvatarWatch.allowed("file:///C:/secret.png"));
    }
}
