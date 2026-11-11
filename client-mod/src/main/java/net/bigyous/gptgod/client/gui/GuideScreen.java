package net.bigyous.gptgod.client.gui;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.bigyous.gptgod.client.gui.GuideProtocol.Block;
import net.bigyous.gptgod.client.gui.GuideProtocol.Chapter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The guide to the gods for players with the mod, in the God Manager's style: the gods of your world and
 * the chapters on the left (operators' chapters under their own heading), one chapter on the right.
 * Clicking a command types it into chat (it is never sent). Left/Right or the buttons turn chapters; Esc
 * closes. It never pauses the game. Everything shown comes from the plugin's "guide" message.
 */
public final class GuideScreen extends Screen {
    static final int SIDEBAR = 128, HEADER = 18, FOOTER = 20, ROW = 13;
    static final int CMD = 0xFFC9A8FF;

    final GuideProtocol.Guide guide;
    final Ui ui = new Ui();
    private int shown = -1;

    public GuideScreen(GuideProtocol.Guide guide) {
        super(Component.literal(guide.title()));
        this.guide = guide;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        ui.press(e);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        ui.release();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        ui.wheel(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_LEFT || e.key() == InputConstants.KEY_PAGEUP) {
            turn(-1);
            return true;
        }
        if (e.key() == InputConstants.KEY_RIGHT || e.key() == InputConstants.KEY_PAGEDOWN) {
            turn(1);
            return true;
        }
        return super.keyPressed(e);
    }

    void turn(int by) {
        int n = guide.chapters().size();
        GuideClient.chapter = Math.max(0, Math.min(n - 1, GuideClient.chapter + by));
    }

    // ---- Drawing ---------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ui.begin(g, font, mouseX, mouseY, a);
        int w = Math.min(width - 12, 600), h = Math.min(height - 12, 340);
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF000000);
        g.fill(x0, y0, x0 + w, y0 + h, Ui.BG);
        g.fill(x0, y0, x0 + SIDEBAR, y0 + h, Ui.PANEL);
        int at = Math.max(0, Math.min(guide.chapters().size() - 1, GuideClient.chapter));
        if (at != shown) {
            shown = at;
            ui.scrollTo("guide", 0);
        }
        sidebar(x0, y0, h, at);
        Chapter c = guide.chapters().get(at);
        int cx = x0 + SIDEBAR, cw = w - SIDEBAR;
        ui.text(ui.fit(c.title(), cw - 60), cx + 8, y0 + 5, 0xFFFFFFFF);
        String count = (at + 1) + " / " + guide.chapters().size();
        ui.small(count, cx + cw - 8 - ui.smallWidth(count), y0 + 7, Ui.DIM);
        g.fill(cx, y0 + HEADER, cx + cw, y0 + HEADER + 1, Ui.LINE);
        int px = cx + 10, py = y0 + HEADER + 6, pw = cw - 20, ph = h - HEADER - FOOTER - 8;
        int off = ui.scrollBegin("guide", cx, py, cw, ph);
        int end = chapter(c, px, py - off, pw);
        ui.scrollEnd("guide", cx, py, cw, ph, end - (py - off) + 4);
        footer(cx, y0 + h - FOOTER, cw, at);
        ui.end();
    }

    private void sidebar(int x, int y, int h, int at) {
        ui.text(ui.fit("✦ " + guide.title(), SIDEBAR - 12), x + 6, y + 5, Ui.ACCENT);
        int cy = y + HEADER + 2;
        for (GuideProtocol.God god : guide.gods().subList(0, Math.min(3, guide.gods().size()))) {
            String name = god.name() + (god.slain() ? " ☠" : "");
            ui.text(ui.fit(name, SIDEBAR - 12), x + 6, cy, 0xFF000000 | god.color());
            cy += 10;
            if (god.epithet() != null) {
                ui.small(ui.fitSmall(god.epithet(), SIDEBAR - 12), x + 6, cy, Ui.DIM);
                cy += 9;
            }
            cy += 2;
        }
        cy = ui.heading("Chapters", x + 6, cy + 2, SIDEBAR - 12);
        List<Chapter> all = guide.chapters();
        int listH = y + h - cy - 4;
        int rows = all.size() + (guide.operatorChapters().isEmpty() ? 0 : 1);
        int off = ui.scrollBegin("side", x, cy, SIDEBAR, listH);
        int ry = cy - off;
        boolean opShown = false;
        for (int i = 0; i < all.size(); i++) {
            Chapter c = all.get(i);
            if (c.op() && !opShown) {
                opShown = true;
                ry = ui.heading("For operators", x + 6, ry + 2, SIDEBAR - 12) - 1;
            }
            boolean on = i == at, hot = ui.hover(x, ry, SIDEBAR, ROW);
            if (on || hot) ui.panel(x, ry, SIDEBAR, ROW, on ? Ui.HOVER : Ui.PANEL2);
            if (on) ui.panel(x, ry, 2, ROW, Ui.ACCENT);
            ui.text(ui.fit(c.title(), SIDEBAR - 16), x + 8, ry + 3, on ? 0xFFFFFFFF : c.op() ? Ui.WARN : Ui.TEXT);
            if (ui.clicked(x, ry, SIDEBAR, ROW)) {
                Ui.click();
                GuideClient.chapter = i;
            }
            ry += ROW;
        }
        ui.scrollEnd("side", x, cy, SIDEBAR, listH, rows * ROW + 6);
    }

    /** Draws the chapter from y; returns the y below it. */
    private int chapter(Chapter c, int x, int y, int w) {
        for (Block b : c.blocks()) {
            switch (b.kind()) {
                case "h" -> y = ui.heading(b.text(), x, y + 3, w) + 1;
                case "cmd" -> y = command(b, x, y, w) + 4;
                default -> y += ui.wrap(b.text(), x, y, w, Ui.TEXT, 0) + 6;
            }
        }
        return y;
    }

    private int command(Block b, int x, int y, int w) {
        int textH = ui.smallHeight(b.text(), w - 12, 0);
        int boxH = 14 + textH + 3;
        boolean typable = b.cmd().startsWith("/");
        boolean hot = typable && ui.hover(x, y, w, boxH);
        ui.panel(x, y, w, boxH, hot ? Ui.HOVER : Ui.PANEL2);
        ui.panel(x, y, 2, boxH, typable ? Ui.ACCENT : Ui.LINE);
        ui.text(ui.fit(b.cmd(), w - 12), x + 7, y + 3, typable ? CMD : Ui.WARN);
        ui.wrapSmall(b.text(), x + 7, y + 14, w - 12, Ui.DIM, 0);
        if (hot) ui.tip(List.of("Click to type it in chat"));
        if (typable && ui.clicked(x, y, w, boxH)) {
            Ui.click();
            minecraft.gui.setScreen(new ChatScreen(GuideProtocol.typed(b.cmd()), false));
        }
        return y + boxH;
    }

    private void footer(int x, int y, int w, int at) {
        ui.panel(x, y, w, 1, Ui.LINE);
        int n = guide.chapters().size();
        if (ui.button("‹ Previous", x + 8, y + 4, 70, 13, Ui.NORMAL, at > 0)) turn(-1);
        if (ui.button("Next ›", x + w - 78, y + 4, 70, 13, Ui.PRIMARY, at < n - 1)) turn(1);
        String hint = "←/→ turn · Esc closes · /guide opens it again";
        ui.small(hint, x + (w - ui.smallWidth(hint)) / 2, y + 8, Ui.FAINT);
    }
}
