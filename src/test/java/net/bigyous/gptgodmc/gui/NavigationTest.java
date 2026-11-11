package net.bigyous.gptgodmc.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.gui.MenuModel.Decision;
import net.bigyous.gptgodmc.gui.MenuModel.Icon;
import net.bigyous.gptgodmc.gui.MenuModel.Kind;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Page;
import net.bigyous.gptgodmc.gui.MenuModel.State;

/** The God Manager is a tree: the hub, each god's page below it, and every page a short way from the hub. */
class NavigationTest {
    /** Clicks from the hub to each page, following every icon that opens one. */
    static Map<Page, Integer> distances(State s) {
        Map<Page, Integer> dist = new EnumMap<>(Page.class);
        dist.put(Page.HUB, 0);
        ArrayDeque<Page> todo = new ArrayDeque<>();
        todo.add(Page.HUB);
        while (!todo.isEmpty()) {
            Page p = todo.poll();
            Menu m = MenuLayout.build(p, s);
            for (int slot = 0; slot < MenuModel.SIZE; slot++) {
                Decision d = MenuLayout.click(m, slot, null);
                if (d.open() != null && !dist.containsKey(d.open())) {
                    dist.put(d.open(), dist.get(p) + 1);
                    todo.add(d.open());
                }
            }
        }
        return dist;
    }

    @Test
    void everyPageIsReachableFromTheHubWithinThreeClicks() {
        Map<Page, Integer> dist = distances(MenuSamples.sample());
        assertEquals(EnumSet.allOf(Page.class), dist.keySet());
        for (Map.Entry<Page, Integer> e : dist.entrySet()) {
            assertTrue(e.getValue() <= 3, e.getKey() + " is " + e.getValue() + " clicks deep");
            assertEquals(e.getKey().depth(), e.getValue(), e.getKey() + ": its depth matches the clicks to it");
        }
    }

    @Test
    void backFromAnyPageLeadsUpToTheHub() {
        State s = MenuSamples.sample();
        for (Page start : Page.values()) {
            Page p = start;
            int steps = 0;
            while (p != Page.HUB) {
                Page up = MenuLayout.click(MenuLayout.build(p, s), MenuModel.BACK_SLOT, null).open();
                assertNotNull(up, p + " has no Back");
                assertEquals(p.parent, up, p + ": Back goes to its parent");
                p = up;
                assertTrue(++steps <= 3, start + " never reaches the hub");
            }
        }
    }

    @Test
    void serverWideSettingsLiveOnlyOnServerPages() {
        State s = MenuSamples.sample();
        Set<Kind> serverKinds = EnumSet.of(Kind.TUNE, Kind.TAB, Kind.TOGGLE_SETTING, Kind.DRAFT_CREATE);
        for (Page page : Page.values()) {
            Menu m = MenuLayout.build(page, s);
            for (int slot = 0; slot < MenuModel.SIZE; slot++) {
                Icon icon = m.at(slot);
                if (icon == null) continue;
                if (!page.serverWide) {
                    assertFalse(serverKinds.contains(icon.action().kind()),
                            page + " slot " + slot + " changes the whole server");
                    Page opens = MenuLayout.click(m, slot, null).open();
                    assertFalse(opens != null && opens.serverWide && opens != Page.HUB,
                            page + " slot " + slot + " opens a server page from inside a god");
                }
            }
        }
        // And from the hub, the server's pages are one click away.
        Map<Page, Integer> dist = distances(s);
        for (Page p : Page.values()) {
            if (p.serverWide && p != Page.HUB) assertEquals(1, dist.get(p), p.name());
        }
    }

    @Test
    void theBottomRowIsTheSameEverywhere() {
        State s = MenuSamples.sample();
        for (Page page : Page.values()) {
            Menu m = MenuLayout.build(page, s);
            assertEquals(Kind.CLOSE, m.at(MenuModel.CLOSE_SLOT).action().kind(), page.name());
            if (page != Page.HUB) assertEquals("Back", m.at(MenuModel.BACK_SLOT).name(), page.name());
            if (page.depth() >= 2) assertEquals("Home", m.at(MenuModel.HOME_SLOT).name(), page.name());
        }
    }

    @Test
    void everyPaintedIconExistsAndIsInThePack() {
        State s = MenuSamples.sample();
        for (Page page : Page.values()) {
            for (Icon icon : MenuLayout.build(page, s).icons().values()) {
                if (icon.art() != null) assertNotNull(MenuIcons.modelFor(icon.art()), page + ": " + icon.art());
            }
        }
        Map<String, byte[]> files = MenuIcons.files();
        for (String key : MenuIcons.KEYS) {
            assertTrue(files.containsKey(MenuIcons.itemFile(key)), key);
            assertTrue(files.containsKey("assets/gptgodmc/textures/item/menu/" + key + ".png"), key);
            BufferedImage img = MenuIcons.image(key);
            assertEquals(MenuIcons.SIZE, img.getWidth());
            assertEquals(0, img.getRGB(0, 0) >>> 24, key + ": transparent corners");
            // The glyph is drawn: plenty of bright pixels inside the face.
            int bright = 0;
            for (int y = 6; y < 26; y++) {
                for (int x = 6; x < 26; x++) {
                    int rgb = img.getRGB(x, y);
                    if (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255) > 300) bright++;
                }
            }
            assertTrue(bright > 25, key + " has a glyph (" + bright + ")");
            // Only our own namespace: no vanilla item or texture is replaced.
            files.keySet().forEach(f -> assertTrue(f.startsWith("assets/gptgodmc/"), f));
        }
    }
}
