package net.bigyous.gptgod.client.gui;

import java.util.List;

import net.bigyous.gptgod.client.gui.GuiProtocol.Entry;
import net.bigyous.gptgod.client.gui.GuiProtocol.Mortal;
import net.bigyous.gptgod.client.gui.GuiProtocol.MortalRow;
import net.bigyous.gptgod.client.gui.GuiProtocol.Mortals;

/**
 * A god's Mortals tab: a searchable list of everyone it keeps a record on, and beside it the record of
 * the one picked: favor as a slider, title, summary, notes and deeds, each edited in place.
 */
final class MortalsPage {
    private MortalsPage() {
    }

    static final int LIST = 128;

    static void draw(ManagerScreen s, String realm, int x, int y, int w, int h) {
        list(s, realm, x, y, LIST, h);
        s.ui.g.fill(x + LIST + 4, y, x + LIST + 5, y + h, Ui.LINE);
        editor(s, realm, x + LIST + 10, y, w - LIST - 10, h);
    }

    private static void list(ManagerScreen s, String realm, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        ui.box("m.search", x, y, w, 15, "Search or name…", 32);
        Mortals ms = m.mortals.get(realm);
        // Enter opens the first match, or asks the server for a record on a name it has never met.
        if (ui.submitted("m.search")) {
            String typed = ui.value("m.search").trim();
            String exact = null;
            if (ms != null) {
                for (MortalRow r : ms.rows()) if (r.name().equalsIgnoreCase(typed)) exact = r.name();
                if (exact == null && ms.rows().size() == 1) exact = ms.rows().get(0).name();
            }
            if (exact != null) m.openMortal.put(realm, exact);
            else if (!typed.isEmpty()) GuiClient.act("m.open", realm, "mortal_open", null, "player", typed);
        }
        String opened = m.take("m.open");
        if (opened != null) m.openMortal.put(realm, opened);
        int cy = y + 17;
        cy += Pages.fieldError(s, "m.open", "player", x, cy, w);
        if (ms == null) {
            Pages.waiting(ui, x, cy + 2);
            return;
        }
        boolean pager = ms.pages() > 1;
        int listH = y + h - cy - (pager ? 16 : 0);
        String open = m.openMortal.get(realm);
        int off = ui.scrollBegin("m.list", x, cy, w, listH);
        int ly = cy - off;
        for (MortalRow r : ms.rows()) {
            boolean on = r.name().equalsIgnoreCase(open);
            boolean hot = ui.hover(x, ly, w - 3, 14);
            if (on || hot) ui.panel(x, ly, w - 3, 14, on ? Ui.HOVER : Ui.PANEL2);
            if (on) ui.panel(x, ly, 2, 14, Ui.ACCENT);
            ui.playerHead(r.name(), x + 4, ly + 2, 10);
            if (r.online()) ui.g.fill(x + 12, ly + 10, x + 15, ly + 13, Ui.OK);
            String favor = (r.favor() > 0 ? "+" : "") + r.favor();
            ui.text(ui.fit(r.name(), w - 40), x + 18, ly + 3, Pages.tierColor(r.tier()));
            ui.small(favor, x + w - 6 - ui.smallWidth(favor), ly + 4, Pages.tierColor(r.tier()));
            if (hot) {
                ui.tip(List.of("§a" + r.name(), "Favor " + favor + " (" + r.tier() + ")",
                        r.title() == null ? "§dNo title" : "Title: " + r.title(),
                        r.online() ? "Online now" : r.seen() == null ? "Not seen lately" : "Last seen " + r.seen() + " ago",
                        r.notes() + " notes, " + r.deeds() + " deeds"));
            }
            if (ui.clicked(x, ly, w - 3, 14)) {
                Ui.click();
                m.openMortal.put(realm, r.name());
            }
            ly += 14;
        }
        if (ms.rows().isEmpty()) {
            ui.wrapSmall(ms.query().isEmpty() ? "The god keeps a record on every mortal it notices. None so far."
                    : "No one matches. Press Enter to open a record on that name.", x + 2, cy + 2, w - 6, Ui.FAINT, 4);
        }
        ui.scrollEnd("m.list", x, cy, w, listH, ms.rows().size() * 14);
        if (pager) {
            int py = y + h - 14;
            int page = ms.page();
            if (ui.button("‹", x, py, 16, 13, Ui.NORMAL, page > 0)) s.pick.put("m.page", String.valueOf(page - 1));
            String label = (page + 1) + " / " + ms.pages() + "  (" + ms.total() + ")";
            ui.small(label, x + (w - ui.smallWidth(label)) / 2, py + 3, Ui.DIM);
            if (ui.button("›", x + w - 16, py, 16, 13, Ui.NORMAL, page < ms.pages() - 1)) {
                s.pick.put("m.page", String.valueOf(page + 1));
            }
        }
    }

    private static void editor(ManagerScreen s, String realm, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        String open = m.openMortal.get(realm);
        Mortal one = m.mortal.get(realm);
        if (open == null) {
            ui.wrapSmall("Pick a mortal on the left to see what the god remembers about them, and to change it.", x, y + 2, w,
                    Ui.FAINT, 3);
            return;
        }
        if (one == null || one.name() == null || !one.name().equalsIgnoreCase(open)) {
            Pages.waiting(ui, x, y + 2);
            return;
        }
        String who = one.name();
        int fh = Pages.feedback(s, "m.edit", x, y, w, 2);
        int top = y + (fh > 0 ? fh + 3 : 0);
        int areaH = y + h - top;
        int off = ui.scrollBegin("m.editor", x, top, w, areaH);
        int cw = w - 6;
        int cy = top - off;
        int start = cy;

        ui.playerHead(who, x, cy, 24);
        ui.text(who, x + 30, cy, Pages.tierColor(one.tier()));
        ui.small(one.online() ? "online now" : one.seen() == null ? "not seen lately" : "last seen " + one.seen() + " ago",
                x + 30, cy + 11, one.online() ? Ui.OK : Ui.DIM);
        ui.small(one.offerings() + " offering" + (one.offerings() == 1 ? "" : "s") + " made", x + 30, cy + 19, Ui.DIM);
        cy += 30;

        // Favor.
        String fid = "m.favor." + realm + "." + who;
        int shown = ui.sliding(fid, one.favor());
        cy = ui.heading("Favor: " + (shown > 0 ? "+" : "") + shown + (shown == one.favor() ? " (" + one.tier() + ")" : ""), x, cy, cw);
        int color = shown <= -6 ? 0xE0354A : shown <= -3 ? 0xFF8A3A : shown < 3 ? 0xD0D0DA : shown < 6 ? 0xFFC23A : 0xFFF2B0;
        Integer set = ui.slider(fid, x, cy, cw, -10, 10, one.favor(), color, 0);
        if (set != null) GuiClient.act("m.edit", realm, "favor", null, "player", who, "value", String.valueOf(set));
        ui.small("despised", x, cy + 12, Ui.FAINT);
        ui.small("chosen", x + cw - ui.smallWidth("chosen"), cy + 12, Ui.FAINT);
        cy += 24;

        // Title.
        cy = ui.heading("Title", x, cy, cw);
        String tid = "m.title." + realm + "." + who;
        ui.box(tid, x, cy, cw - 40, 15, "No title", 40);
        s.prefill(tid, one.title() == null ? "" : one.title());
        if (ui.button("Set", x + cw - 36, cy, 36, 15) || ui.submitted(tid)) {
            GuiClient.act("m.edit", realm, "title_set", null, "player", who, "title", ui.value(tid).trim());
        }
        cy += 17 + Pages.fieldError(s, "m.edit", "title", x, cy + 17, cw) + 3;

        // Summary.
        cy = ui.heading("Their past, as it remembers it", x, cy, cw);
        String sid = "m.summary." + realm + "." + who;
        if (s.flags.contains(sid)) {
            ui.box(sid, x, cy, cw - 76, 15, "Empty: no remembered past", 1200);
            s.prefill(sid, one.summary() == null ? "" : one.summary());
            if (ui.button("Save", x + cw - 72, cy, 34, 15) || ui.submitted(sid)) {
                GuiClient.act("m.edit", realm, "summary_set", null, "player", who, "summary", ui.value(sid).trim());
                s.flags.remove(sid);
                s.filled.remove(sid);
            }
            if (ui.button("Cancel", x + cw - 36, cy, 36, 15)) {
                s.flags.remove(sid);
                s.filled.remove(sid);
            }
            cy += 19;
        } else {
            int sh = one.summary() == null ? ui.wrapSmall("Nothing yet.", x, cy, cw - 40, Ui.FAINT, 1)
                    : ui.wrapSmall(one.summary(), x, cy, cw - 40, Ui.TEXT, 8);
            if (ui.button("Edit", x + cw - 36, cy, 36, 13)) s.flags.add(sid);
            cy += Math.max(15, sh + 3);
        }

        cy = entries(s, realm, who, "note", "Notes (thoughts it holds about them)", one.notes(), one.notesTotal(), x, cy + 2, cw);
        cy = entries(s, realm, who, "deed", "Deeds (what they did)", one.deeds(), one.deedsTotal(), x, cy + 2, cw);

        cy = ui.heading("Forgetting", x, cy + 2, cw);
        int bw = (cw - 6) / 3;
        if (ui.confirm("m.cn." + who, "Clear notes", x, cy, bw, 15)) {
            GuiClient.act("m.edit", realm, "notes_clear", "true", "player", who);
        }
        if (ui.confirm("m.cd." + who, "Clear deeds", x + bw + 3, cy, bw, 15)) {
            GuiClient.act("m.edit", realm, "deeds_clear", "true", "player", who);
        }
        if (ui.confirm("m.fg." + who, "Forget " + who, x + 2 * (bw + 3), cy, bw, 15)) {
            GuiClient.act("m.edit", realm, "forget", "true", "player", who);
        }
        ui.tipIf(x + 2 * (bw + 3), cy, bw, 15, "Erase everything: favor, title, notes, deeds, offerings, words. "
                + "(Its annals, the permanent log, still record what happened.)");
        cy += 20;
        ui.scrollEnd("m.editor", x, top, w, areaH, cy - start);
    }

    /** A list of notes or deeds with an x on each, and a box to add one. Returns the y below it. */
    private static int entries(ManagerScreen s, String realm, String who, String kind, String title, List<Entry> list,
            int total, int x, int y, int w) {
        Ui ui = s.ui;
        int cy = ui.heading(title + ": " + total, x, y, w);
        for (Entry e : list) {
            int th = ui.smallHeight(e.text(), w - 50, 4);
            boolean hot = ui.hover(x, cy, w, th + 2);
            if (hot) ui.panel(x, cy - 1, w, th + 2, Ui.PANEL);
            ui.wrapSmall(e.text(), x + 2, cy, w - 50, Ui.TEXT, 4);
            if (e.age() != null) ui.small(e.age(), x + w - 20 - ui.smallWidth(e.age()), cy, Ui.FAINT);
            String id = "m.del." + kind + "." + e.at() + "." + e.text().hashCode();
            if (ui.confirm(id, "✕", x + w - 16, cy - 1, 16, 10)) {
                GuiClient.act("m.edit", realm, kind + "_del", null, "player", who, "at", String.valueOf(e.at()), "text", e.text());
            }
            cy += th + 3;
        }
        if (total > list.size()) {
            ui.small("…and " + (total - list.size()) + " older ones.", x + 2, cy, Ui.FAINT);
            cy += 9;
        }
        String id = "m.add." + kind + "." + realm + "." + who;
        ui.box(id, x, cy, w - 40, 15, kind.equals("note") ? "Add a thought it will hold about them…"
                : "Record something they did…", 200);
        if (ui.button("Add", x + w - 36, cy, 36, 15) || ui.submitted(id)) {
            GuiClient.act("m.edit", realm, kind + "_add", null, "player", who, "text", ui.value(id).trim());
            ui.set(id, "");
        }
        return cy + 19;
    }
}
