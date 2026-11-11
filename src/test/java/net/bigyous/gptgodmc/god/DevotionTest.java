package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.god.tools.GodTool;
import net.bigyous.gptgodmc.loggables.Loggable;
import net.bigyous.gptgodmc.loggables.OfferingLoggable;

class DevotionTest {

    @Test
    void commandmentsAddRevokeReplaceAndLimits() {
        Memory m = new Memory();
        assertEquals(1, m.addCommandment("  Thou shalt not slay the village cows.\n"));
        assertEquals(2, m.addCommandment("§cHonor the §lshrine"));
        assertEquals("Honor the shrine", m.commandments().get(1));
        assertEquals(3, m.addCommandment("3. Pray at dusk"));
        assertEquals("Pray at dusk", m.commandments().get(2));
        assertTrue(m.renderCommandments().startsWith("1. Thou shalt not slay"), m.renderCommandments());

        assertEquals("Honor the shrine", m.revokeCommandment(2));
        assertEquals(List.of("Thou shalt not slay the village cows.", "Pray at dusk"), m.commandments());
        assertThrows(IllegalArgumentException.class, () -> m.revokeCommandment(5));
        assertThrows(IllegalArgumentException.class, () -> m.addCommandment("x".repeat(Memory.COMMANDMENT_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> m.addCommandment("   "));

        for (int i = m.commandments().size(); i < Memory.MAX_COMMANDMENTS; i++) {
            m.addCommandment("Law " + i);
        }
        assertThrows(IllegalArgumentException.class, () -> m.addCommandment("One too many"));

        m.replaceCommandments(List.of("Share bread", "", "Build no towers taller than mine"));
        assertEquals(2, m.commandments().size());
        assertThrows(IllegalArgumentException.class, () -> m.replaceCommandments(List.of("a", "b", "c", "d", "e",
                "f", "g", "h")));
        assertEquals(2, m.commandments().size(), "a failed replace leaves the laws as they were");
        m.replaceCommandments(List.of());
        assertTrue(m.renderCommandments().isEmpty());
    }

    @Test
    void perceptionShowsCommandmentsTitlesAndMarks() {
        Memory m = new Memory();
        assertTrue(Perception.commandmentsBlock(m).contains("(none decreed)"));
        m.addCommandment("Feed the wolves");
        String block = Perception.commandmentsBlock(m);
        assertTrue(block.startsWith("## Your commandments"), block);
        assertTrue(block.contains("1. Feed the wolves"), block);

        long now = System.currentTimeMillis();
        assertEquals("", Perception.devotionOf(m, "Steve", now));
        m.setTitle("Steve", "the Faithful", "gold");
        m.setMark("Steve", Marks.HALO, now + 5 * 60_000);
        String line = Perception.devotionOf(m, "steve", now);
        assertTrue(line.contains("titled \"the Faithful\""), line);
        assertTrue(line.contains("golden halo, 5 min left"), line);

        assertFalse(m.expireMark("Steve", now));
        assertNull(m.activeMark("Steve", now + 6 * 60_000));
        assertTrue(m.expireMark("Steve", now + 6 * 60_000));
        assertFalse(Perception.devotionOf(m, "Steve", now).contains("halo"));

        assertEquals("the Faithful", m.setTitle("Steve", "", null));
        assertNull(m.titleOf("Steve"));
        assertNull(m.setTitle("Nobody", null, null));
        assertNull(m.subjectIfAny("Nobody"), "revoking from a stranger does not create a record");
    }

    @Test
    void titlesAreSanitised() {
        assertEquals("the Faithful", Titles.sanitize("  §6the &lFaithful\n"));
        assertEquals("Oathbreaker", Titles.sanitize("\"Oathbreaker\""));
        assertEquals("the Red", Titles.sanitize("<red>the Red</red>"));
        assertEquals("a b", Titles.sanitize("a\t​  b"));
        assertEquals("", Titles.sanitize(null));
        assertEquals("light_purple", Titles.color("Purple"));
        assertEquals("gold", Titles.color("rainbow"));
        assertEquals("dark_red", Titles.color("DARK RED"));
    }

    @Test
    void offeringsAreRememberedAndRendered() {
        Memory m = new Memory();
        for (int i = 1; i <= 7; i++) {
            assertEquals(i, m.recordOffering("Alex", i + " diamond", i % 2 == 0 ? "vessel" : "sky"));
        }
        Memory.Subject s = m.subjectIfAny("alex");
        assertEquals(7, s.offerings);
        assertEquals(5, s.offered.size());
        String ledger = m.renderLedger(List.of());
        assertTrue(ledger.contains("offerings: 7 in all"), ledger);
        assertTrue(ledger.contains("7 diamond"), ledger);
        assertTrue(ledger.contains("6 diamond at your vessel"), ledger);
        assertFalse(ledger.contains("no notes yet"), ledger);
    }

    @Test
    void offeringLoggableTextAndCombine() {
        OfferingLoggable a = new OfferingLoggable("Alex", "12 diamond", OfferingLoggable.TO_SKY, false, 1, true);
        assertEquals(Loggable.Category.PRAYER, a.category());
        assertNotNull(a.attentionReason());
        String text = a.getLog();
        assertTrue(text.contains("Alex made an OFFERING to you: 12 diamond, lifted up to the sky"), text);
        assertTrue(text.contains("first offering"), text);

        OfferingLoggable b = new OfferingLoggable("Alex", "1 golden_apple named \"For You\"",
                OfferingLoggable.TO_SKY, false, 2, false);
        assertNull(b.attentionReason());
        assertTrue(a.combine(b));
        text = a.getLog();
        assertTrue(text.contains("2 OFFERINGS"), text);
        assertTrue(text.contains("12 diamond, 1 golden_apple named \"For You\""), text);

        assertFalse(a.combine(new OfferingLoggable("Steve", "1 dirt", OfferingLoggable.TO_SKY, false, 1, true)));
        assertFalse(a.combine(new OfferingLoggable("Alex", "1 dirt", OfferingLoggable.AT_VESSEL, false, 3, true)));

        OfferingLoggable c = new OfferingLoggable("Alex", "64 cobblestone", OfferingLoggable.AT_VESSEL, true, 9, true);
        c.arm();
        assertFalse(c.seen());
        assertTrue(c.getLog().contains("cost them nothing"));
        assertTrue(c.getLog().contains("9 offerings in all"));
        assertTrue(c.seen());
        assertFalse(c.combine(new OfferingLoggable("Alex", "1 dirt", OfferingLoggable.AT_VESSEL, false, 10, false)),
                "once the god may have read it, a new gift needs its own event");
    }

    @Test
    void devotionToolsAreRegisteredLastAndDescribed() {
        List<GodTool> all = new java.util.ArrayList<>(ToolRegistry.all());
        List<String> names = all.stream().map(GodTool::name).toList();
        int decree = names.indexOf("decree");
        assertTrue(decree > 0, names.toString());
        assertEquals("bestow_title", names.get(decree + 1));
        assertEquals("mark", names.get(decree + 2));
        for (GodTool t : all.subList(decree, decree + 3)) {
            assertTrue(t.description().length() > 80, t.name());
            t.schema().properties().forEach((k, v) -> assertNotNull(v.get("description"), t.name() + "." + k));
        }
    }

    /** Halo and curse as dots around a player silhouette, seen from the side: build/test-renders/particles-marks.png. */
    @Test
    void marksPreview() throws Exception {
        int w = 480, h = 300, scale = 90;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x7FA7D9));
        g.fillRect(0, 0, w, h);
        String[] kinds = { Marks.HALO, Marks.CURSE };
        for (int k = 0; k < 2; k++) {
            int cx = 120 + k * 240, ground = 270;
            g.setColor(new Color(0x3C2A1E));
            g.fillRect(cx - 18, ground - (int) (1.8 * scale) + 36, 36, (int) (1.8 * scale) - 36); // body
            g.setColor(new Color(0xC69C6D));
            g.fillRect(cx - 18, ground - (int) (1.8 * scale), 36, 36); // head
            for (int frame = 0; frame < 3; frame++) {
                List<Vector> pts = Marks.points(kinds[k], 1.8, frame * 3);
                for (Vector v : pts) {
                    // Slight tilt so the ring reads as a ring: screen y uses height and depth.
                    int x = cx + (int) Math.round(v.getX() * scale);
                    int y = ground - (int) Math.round(v.getY() * scale - v.getZ() * scale * 0.3);
                    int r = k == 0 ? 4 : 7;
                    g.setColor(k == 0 ? new Color(255, 217, 102, 255 - frame * 60)
                            : new Color(42, 10, 51, 255 - frame * 60));
                    g.fillOval(x - r, y - r, r * 2, r * 2);
                }
            }
            g.setColor(Color.DARK_GRAY);
            g.setStroke(new BasicStroke(1));
            g.drawString(kinds[k], cx - 14, 20);
        }
        g.dispose();
        File out = new File("build/test-renders");
        out.mkdirs();
        ImageIO.write(img, "png", new File(out, "particles-marks.png"));
        assertEquals(8, Marks.points(Marks.HALO, 1.8, 0).size());
        assertTrue(Marks.points(Marks.HALO, 1.8, 0).get(0).getY() > 1.8, "the halo floats above the head");
    }
}
