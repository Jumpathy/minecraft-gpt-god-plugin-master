package net.bigyous.gptgod.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import net.bigyous.gptgod.client.gui.GuiModel.Route;
import net.bigyous.gptgod.client.gui.GuiProtocol.God;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The God Manager for operators with the client mod: a sidebar with every god's head and the server
 * pages, tabs for the chosen god, and one page of real controls. Back (the button, Esc, the mouse's back
 * button) returns to the previous page and closes the screen only from the first one. It never pauses
 * the game. Everything shown comes from the plugin; every click is a request the plugin answers.
 */
public final class ManagerScreen extends Screen {
    static final int SIDEBAR = 104, HEADER = 18, TABS = 15, STATUS = 12;
    static final String[] TAB_NAMES = { "Overview", "Commands", "Mortals", "Nature", "Body", "Mind", "Journal" };

    final GuiModel m = GuiClient.MODEL;
    final Ui ui = new Ui();
    /** Page state that is not the server's: picks not yet sent, toggles, what was already typed in. */
    final Map<String, String> pick = new LinkedHashMap<>();
    final Set<String> flags = new HashSet<>();
    final Set<String> filled = new HashSet<>();
    int history = -1;
    private String subscribed = "";
    private long subscribedAt;
    private String focusNext;
    /** Text waiting to be put into boxes once they are drawn (previews and tests). */
    private final Map<String, String> typeLater = new LinkedHashMap<>();
    /** Where things were drawn last frame, in GUI pixels: x, y, w, h (for tests that point at them). */
    public final Map<String, int[]> marks = new LinkedHashMap<>();

    public ManagerScreen() {
        super(Component.literal("God Manager"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void onClose() {
        GuiClient.closed();
        minecraft.gui.setScreen(null);
    }

    /** One page back; from the first page, out of the screen. */
    void goBack() {
        if (!m.back()) onClose();
    }

    /** Puts the cursor in a text box as soon as it is on screen (also one a page change is about to show). */
    void focusLater(String id) {
        focusNext = id;
    }

    /** Puts text into a box as soon as it is on screen. */
    public void type(String boxId, String text) {
        typeLater.put(boxId, text);
    }

    /** Arms a confirm button (as after its first click). */
    public void arm(String id) {
        ui.arm(id);
    }

    public Ui ui() {
        return ui;
    }

    /** Sets a text box's text the first time it is shown (e.g. a title to edit). */
    void prefill(String id, String value) {
        if (filled.add(id)) ui.set(id, value);
    }

    // ---- Input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == InputConstants.MOUSE_BUTTON_4) {
            goBack();
            return true;
        }
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
        if (e.hasControlDown()) {
            int digit = e.key() - InputConstants.KEY_1;
            if (digit >= 0 && digit < GuiModel.GOD_TABS.size() && m.route().ofGod()) {
                m.tab(GuiModel.GOD_TABS.get(digit));
                return true;
            }
            if (e.key() == InputConstants.KEY_F) {
                focusNext = switch (m.route().page()) {
                    case GuiModel.MORTALS -> "m.search";
                    case GuiModel.JOURNAL -> "j.filter";
                    case GuiModel.CONFIG -> "cfg.search";
                    case GuiModel.COMMANDS -> "cmd";
                    default -> null;
                };
                return true;
            }
        }
        if (ui.key(e)) return true;
        if (e.isEscape()) {
            goBack();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return ui.typed(e);
    }

    // ---- Drawing ---------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ui.begin(g, font, mouseX, mouseY, a);
        int w = Math.min(width - 12, 640), h = Math.min(height - 12, 360);
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF000000);
        g.fill(x0, y0, x0 + w, y0 + h, Ui.BG);
        g.fill(x0, y0, x0 + SIDEBAR, y0 + h, Ui.PANEL);

        Route r = m.route();
        sidebar(x0, y0, h, r);
        int cx = x0 + SIDEBAR, cw = w - SIDEBAR;
        header(cx, y0, cw, r);
        int top = y0 + HEADER;
        if (r.ofGod()) {
            tabs(cx, top, cw, r);
            top += TABS;
        }
        g.fill(cx, top, cx + cw, top + 1, Ui.LINE);
        int px = cx + 8, py = top + 6, pw = cw - 16, ph = y0 + h - STATUS - py - 2;
        String realm = r.realm();
        switch (r.page()) {
            case GuiModel.OVERVIEW -> GodPages.overview(this, realm, px, py, pw, ph);
            case GuiModel.COMMANDS -> GodPages.commands(this, realm, px, py, pw, ph);
            case GuiModel.MORTALS -> MortalsPage.draw(this, realm, px, py, pw, ph);
            case GuiModel.NATURE -> GodPages.nature(this, realm, px, py, pw, ph);
            case GuiModel.BODY -> GodPages.body(this, realm, px, py, pw, ph);
            case GuiModel.MIND -> GodPages.mind(this, realm, px, py, pw, ph);
            case GuiModel.JOURNAL -> GodPages.journal(this, realm, px, py, pw, ph);
            case GuiModel.CREATE -> ServerPages.create(this, px, py, pw, ph);
            case GuiModel.SWITCHES -> ServerPages.switches(this, px, py, pw, ph);
            case GuiModel.CONFIG -> ServerPages.config(this, px, py, pw, ph);
            case GuiModel.COSTS -> ServerPages.costs(this, px, py, pw, ph);
            default -> ServerPages.pantheon(this, px, py, pw, ph);
        }
        status(cx, y0 + h - STATUS, cw);
        typeLater.entrySet().removeIf(e -> {
            if (!ui.has(e.getKey())) return false;
            filled.add(e.getKey());
            ui.set(e.getKey(), e.getValue());
            return true;
        });
        if (focusNext != null && ui.has(focusNext)) {
            ui.focus(focusNext);
            focusNext = null;
        }
        ui.end();
        subscribe();
    }

    private void sidebar(int x, int y, int h, Route r) {
        boolean home = r.page().equals(GuiModel.PANTHEON);
        if (row("✦ God Manager", x, y, SIDEBAR, HEADER, home, Ui.ACCENT)) m.go(GuiModel.PANTHEON, null);
        int cy = ui.heading("Gods", x + 6, y + HEADER + 4, SIDEBAR - 12);
        // Server rows sit at the bottom; the gods scroll in what is left.
        int serverRows = 2 + (m.showCosts ? 1 : 0) + (m.mayShapePantheon ? 1 : 0);
        int serverTop = y + h - serverRows * 14 - 16;
        int listH = serverTop - cy - 2;
        int off = ui.scrollBegin("side", x, cy, SIDEBAR, listH);
        int gy = cy - off;
        for (God g : m.gods) {
            boolean on = g.id().equals(r.realm());
            boolean hot = ui.hover(x, gy, SIDEBAR, 20);
            if (on || hot) ui.panel(x, gy, SIDEBAR, 20, on ? Ui.HOVER : Ui.PANEL2);
            if (on) ui.panel(x, gy, 2, 20, 0xFF000000 | g.color());
            Pages.head(this, g, x + 6, gy + 2, 1);
            ui.text(ui.fit(g.label(), SIDEBAR - 30), x + 26, gy + 2, 0xFF000000 | (g.name() == null ? 0x9A9AA8 : g.color()));
            ui.small(Pages.stateWord(g), x + 26, gy + 12, Pages.stateColor(g, ui.now));
            if (g.pending() > 0) {
                String n = String.valueOf(g.pending());
                ui.small(n, x + SIDEBAR - 6 - ui.smallWidth(n), gy + 12, Ui.WARN);
            }
            if (hot) ui.tip(Pages.card(g));
            marks.put("side." + g.id(), new int[] { x, gy, SIDEBAR, 20 });
            if (ui.clicked(x, gy, SIDEBAR, 20)) {
                Ui.click();
                // Keep the tab when hopping between gods; from a server page, open the god's overview.
                m.go(r.ofGod() ? r.page() : GuiModel.OVERVIEW, g.id());
            }
            gy += 20;
        }
        ui.scrollEnd("side", x, cy, SIDEBAR, listH, m.gods.size() * 20);

        int sy = ui.heading("Server", x + 6, serverTop, SIDEBAR - 12);
        if (m.mayShapePantheon) {
            if (row("+ New god", x, sy, SIDEBAR, 14, r.page().equals(GuiModel.CREATE), Ui.OK)) m.go(GuiModel.CREATE, null);
            sy += 14;
        }
        if (row("Quick switches", x, sy, SIDEBAR, 14, r.page().equals(GuiModel.SWITCHES), Ui.TEXT)) m.go(GuiModel.SWITCHES, null);
        sy += 14;
        if (row("Config", x, sy, SIDEBAR, 14, r.page().equals(GuiModel.CONFIG), Ui.TEXT)) m.go(GuiModel.CONFIG, null);
        sy += 14;
        if (m.showCosts && row("Costs", x, sy, SIDEBAR, 14, r.page().equals(GuiModel.COSTS), Ui.TEXT)) {
            m.go(GuiModel.COSTS, null);
        }
    }

    /** A sidebar row; true when clicked. */
    private boolean row(String label, int x, int y, int w, int h, boolean on, int color) {
        boolean hot = ui.hover(x, y, w, h);
        if (on || hot) ui.panel(x, y, w, h, on ? Ui.HOVER : Ui.PANEL2);
        if (on) ui.panel(x, y, 2, h, Ui.ACCENT);
        ui.text(ui.fit(label, w - 12), x + 8, y + (h - 8) / 2, on ? 0xFFFFFFFF : color);
        if (ui.clicked(x, y, w, h)) {
            Ui.click();
            return true;
        }
        return false;
    }

    private void header(int x, int y, int w, Route r) {
        ui.panel(x, y, w, HEADER, Ui.PANEL);
        String crumb;
        int color = Ui.TEXT;
        if (r.ofGod()) {
            God g = m.god(r.realm());
            crumb = (g == null ? "God" : g.label()) + " › " + TAB_NAMES[GuiModel.GOD_TABS.indexOf(r.page())];
            if (g != null && g.name() != null) color = 0xFF000000 | g.color();
        } else {
            crumb = switch (r.page()) {
                case GuiModel.CREATE -> "Server › New god";
                case GuiModel.SWITCHES -> "Server › Quick switches";
                case GuiModel.CONFIG -> "Server › Config";
                case GuiModel.COSTS -> "Server › Costs";
                default -> "Pantheon";
            };
        }
        ui.text(ui.fit(crumb, w - 60), x + 8, y + 5, color);
        if (GuiClient.offline()) ui.small("sample data", x + 12 + ui.width(crumb), y + 7, Ui.WARN);
        boolean can = m.canGoBack();
        if (ui.button("←", x + w - 38, y + 2, 16, 14, Ui.NORMAL, can)) goBack();
        ui.tipIf(x + w - 38, y + 2, 16, 14, can ? "Back to the previous page (Esc)" : null);
        if (ui.button("✕", x + w - 19, y + 2, 16, 14, Ui.NORMAL, true)) onClose();
    }

    private void tabs(int x, int y, int w, Route r) {
        ui.panel(x, y, w, TABS, Ui.PANEL);
        int tx = x + 6;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            String page = GuiModel.GOD_TABS.get(i);
            int tw = ui.width(TAB_NAMES[i]) + 10;
            boolean on = page.equals(r.page());
            boolean hot = ui.hover(tx, y, tw, TABS);
            if (on) ui.panel(tx, y + TABS - 2, tw, 2, Ui.ACCENT);
            ui.text(TAB_NAMES[i], tx + 5, y + 3, on ? 0xFFFFFFFF : hot ? Ui.TEXT : Ui.DIM);
            if (hot) ui.tip(List.of("§dCtrl+" + (i + 1)));
            if (ui.clicked(tx, y, tw, TABS)) {
                Ui.click();
                m.tab(page);
            }
            tx += tw + 2;
        }
    }

    /** The bottom line: the newest message the plugin would otherwise have put in chat. */
    private void status(int x, int y, int w) {
        ui.panel(x, y, w, STATUS, Ui.PANEL);
        GuiModel.Notice n = m.latest(ui.now, 12_000);
        if (n != null) {
            ui.small(ui.fitSmall(n.text(), w - 12), x + 8, y + 3, n.error() ? Ui.BAD : Ui.DIM);
        } else {
            ui.small("Esc: back · Ctrl+1-7: tabs · Ctrl+F: search", x + 8, y + 3, Ui.FAINT);
        }
    }

    // ---- Keeping the server's pushes on this page ---------------------------------------------------

    private void subscribe() {
        Route r = m.route();
        Map<String, String> args = new LinkedHashMap<>();
        if (r.page().equals(GuiModel.MORTALS)) {
            args.put("query", ui.value("m.search").trim());
            args.put("page", pick.getOrDefault("m.page", "0"));
            String open = m.openMortal.get(r.realm());
            if (open != null) args.put("player", open);
        }
        String key = r.page() + "|" + r.realm() + "|" + args;
        if (key.equals(subscribed)) return;
        // Typing in a search box: at most a few subscriptions a second.
        if (ui.now - subscribedAt < 200 && subscribed.startsWith(r.page() + "|" + r.realm())) return;
        subscribed = key;
        subscribedAt = ui.now;
        GuiClient.subscribe(r.realm(), GuiModel.part(r.page()), args);
    }

    /** Words wrapped into rows of chips; returns the y below them. draw is called for each chip's place. */
    interface ChipDraw {
        void at(int index, int x, int y);
    }

    int flow(List<String> labels, int x, int y, int w, ChipDraw draw) {
        int cx = x, cy = y;
        for (int i = 0; i < labels.size(); i++) {
            int cw = ui.chipWidth(labels.get(i));
            if (cx + cw > x + w && cx > x) {
                cx = x;
                cy += 16;
            }
            draw.at(i, cx, cy);
            cx += cw + 3;
        }
        return cy + 16;
    }

    static List<String> list(String... s) {
        return new ArrayList<>(List.of(s));
    }
}
