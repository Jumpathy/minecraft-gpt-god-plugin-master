package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.gui.MenuLayout;
import net.bigyous.gptgodmc.gui.MenuModel;
import net.bigyous.gptgodmc.gui.MenuModel.Icon;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Page;
import net.bigyous.gptgodmc.gui.MenuSamples;

/**
 * Each God Manager page as the game draws it (needs the local 26.3 client jar; skipped without it):
 * build/test-renders/chest-<page>.png with the plugin's pack (painted icons), and chest-<page>-vanilla.png
 * as a player without it (or on Bedrock) sees it. One icon per page is hovered to show its tooltip.
 */
class ChestPreviewTest {
    @Test
    void drawEveryPage() throws Exception {
        Assumptions.assumeTrue(ChestRender.available(), "no Minecraft 26.3 client jar");
        File out = new File("build/test-renders");
        out.mkdirs();
        try (ChestRender r = new ChestRender()) {
            for (Page page : Page.values()) {
                Menu m = MenuLayout.build(page, MenuSamples.sample());
                int hover = hover(page, m);
                for (boolean pack : new boolean[] { true, false }) {
                    File f = new File(out, "chest-" + page.id() + (pack ? "" : "-vanilla") + ".png");
                    ImageIO.write(r.page(m, pack, hover), "png", f);
                    assertTrue(f.length() > 0);
                }
            }
        }
    }

    /** The icon whose tooltip is shown: the page's most telling button. */
    static int hover(Page page, Menu m) {
        switch (page) {
            case HUB -> {
                return 41;
            }
            case MAIN -> {
                return 13;
            }
            default -> {
            }
        }
        for (int slot = 10; slot < MenuModel.SIZE - 9; slot++) {
            Icon i = m.at(slot);
            if (i != null && i.action().kind() != MenuModel.Kind.NONE && slot % 9 != 0 && slot % 9 != 8) return slot;
        }
        return MenuModel.BACK_SLOT;
    }
}
