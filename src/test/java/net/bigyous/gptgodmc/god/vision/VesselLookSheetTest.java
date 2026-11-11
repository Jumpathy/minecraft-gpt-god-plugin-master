package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselSkin;
import net.bigyous.gptgodmc.god.vessel.VesselTextures;

/**
 * Every look on one contact sheet (vessel-look-sheet.png): its painted skin and its block fallback side
 * by side with the name and description; vessel-look-sheet-back.png shows the skins from behind.
 */
class VesselLookSheetTest {
    static final int COLS = 6, CAPTION = 46;

    @Test
    void everyLookIsComplete() {
        Set<String> seen = new HashSet<>();
        for (String name : VesselLook.NAMES) {
            VesselLook look = VesselLook.byName(name);
            assertEquals(name, look.name(), "byName(" + name + ")");
            assertTrue(seen.add(name));
            assertFalse(VesselLook.description(name).isBlank(), "description for " + name);
            for (VesselLook.Slot s : List.of(VesselLook.Slot.SKIN, VesselLook.Slot.EYES, VesselLook.Slot.TORSO,
                    VesselLook.Slot.SLEEVES, VesselLook.Slot.HANDS, VesselLook.Slot.LEGS, VesselLook.Slot.BOOTS,
                    VesselLook.Slot.BELT, VesselLook.Slot.GEM, VesselLook.Slot.CAPE, VesselLook.Slot.CAPE_LINING)) {
                assertTrue(look.material(s) != null, name + " has no " + s);
            }
            if (look.ornament() != VesselLook.Ornament.NONE) {
                assertTrue(look.material(VesselLook.Slot.ORNAMENT) != null, name + " has no ornament block");
            }
            // The aura particles are spawned without data, so every look needs a plain one.
            assertEquals(Void.class, look.sparkle().getDataType(), name + " sparkle " + look.sparkle());
            List<VesselModel.Part> parts = VesselModel.parts(look);
            assertTrue(parts.size() > 20 && parts.size() < 80, name + " part count " + parts.size());
        }
        assertTrue(VesselLook.NAMES.size() >= 20, "looks: " + VesselLook.NAMES.size());
        assertEquals("shade", VesselLook.NAMES.get(0));
    }

    @Test
    void autoReadsTheEpithet() {
        assertEquals("tide", VesselLook.resolve("auto", "evil", "the Drowned King of the Sea").name());
        assertEquals("storm", VesselLook.resolve("auto", "benevolent", "Vael, Caller of Storms").name());
        assertEquals("frost", VesselLook.autoName("chill", "the Winter Crown"));
        assertEquals("allfather", VesselLook.autoName("wrathful", "the One-Eyed Wanderer"));
        assertEquals("radiant", VesselLook.autoName("benevolent", "Oruvex"));
        assertEquals("jester", VesselLook.autoName("trickster", null));
        assertEquals("shade", VesselLook.autoName("custom-archetype", "Qzor"));
        assertEquals("thunder", VesselLook.resolve("Thunder", null).name());
        for (List<String> pool : List.of(VesselLook.suited("benevolent"), VesselLook.suited("evil"),
                VesselLook.suited("cosmic"), VesselLook.suited("nonsense"))) {
            assertTrue(VesselLook.NAMES.containsAll(pool), pool.toString());
        }
        assertEquals(VesselLook.Ornament.ANTLERS, VesselLook.Ornament.parse(" Antlers ", VesselLook.Ornament.NONE));
        assertEquals(VesselLook.Ornament.HALO, VesselLook.Ornament.parse("auto", VesselLook.Ornament.HALO));
    }

    @Test
    void renderSheet() throws IOException {
        int n = VesselLook.NAMES.size(), rows = (n + COLS - 1) / COLS;
        int cw = VesselPreviewTest.CELL_W, ch = VesselPreviewTest.CELL_H;
        VesselPreviewTest.View three = new VesselPreviewTest.View("three-quarter", 0.55, 0.16);
        BufferedImage front = new BufferedImage(COLS * cw * 2, rows * (ch + CAPTION), BufferedImage.TYPE_INT_RGB);
        BufferedImage back = new BufferedImage(COLS * cw, rows * (ch + CAPTION), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = front.createGraphics(), gb = back.createGraphics();
        for (int i = 0; i < n; i++) {
            String name = VesselLook.NAMES.get(i);
            VesselLook look = VesselLook.byName(name);
            VesselTextures.Atlas atlas = VesselTextures.atlas(name);
            List<VesselModel.Placed> parts = VesselModel.place(look,
                    VesselModel.pose("speak", 0, 0, 0, new Vector3f(0.3f, 0, 1), null), 1.0);
            int x = (i % COLS) * cw * 2, y = (i / COLS) * (ch + CAPTION);
            int outline = look.outline().asRGB();
            g.drawImage(VesselPreviewTest.render(parts, three, outline, List.of(), 0, 88, atlas, 0, null), x, y, null);
            g.drawImage(VesselPreviewTest.render(parts, three, outline, List.of(), 0, 88), x + cw, y, null);
            caption(g, name, "skin  |  blocks", VesselLook.description(name), x, y + ch, cw * 2);
            int bx = (i % COLS) * cw;
            gb.drawImage(VesselPreviewTest.render(parts, VesselPreviewTest.VIEWS.get(3), outline, List.of(), 0, 88,
                    atlas, 0, null), bx, y, null);
            caption(gb, name, "back", "", bx, y + ch, cw);
        }
        g.dispose();
        gb.dispose();
        RendererTest.save(front, "vessel-look-sheet");
        RendererTest.save(back, "vessel-look-sheet-back");
        assertTrue(VesselSkin.keys().contains("beard") && VesselSkin.keys().contains("patch"));
    }

    private static void caption(Graphics2D g, String name, String tag, String text, int x, int y, int w) {
        g.setColor(new Color(0x0E0A14));
        g.fillRect(x, y, w, CAPTION);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.setColor(new Color(0xF2E6FF));
        g.drawString(name, x + 8, y + 17);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(new Color(0x9A8AAE));
        g.drawString(tag, x + w - 8 - g.getFontMetrics().stringWidth(tag), y + 16);
        g.setColor(new Color(0xC8BCD8));
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (fm.stringWidth(line + " " + word) > w - 16 && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(lines.get(i), x + 8, y + 31 + i * 12);
    }
}
