package net.bigyous.gptgod.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bigyous.gptgod.client.gui.GuiProtocol.CostGod;
import net.bigyous.gptgod.client.gui.GuiProtocol.Costs;
import net.bigyous.gptgod.client.gui.GuiProtocol.God;
import net.bigyous.gptgod.client.gui.GuiProtocol.Named;
import net.bigyous.gptgod.client.gui.GuiProtocol.Option;
import net.bigyous.gptgod.client.gui.GuiProtocol.Switch;

/** The pages about the whole server: the pantheon, New god, Quick switches, Config and Costs. */
final class ServerPages {
    private ServerPages() {
    }

    // ---- Pantheon ---------------------------------------------------------------------------------

    static void pantheon(ManagerScreen s, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        if (m.gods.isEmpty()) {
            Pages.waiting(ui, x, y);
            return;
        }
        int cw = 126, ch = 62, gap = 6;
        int cols = Math.max(1, (w + gap) / (cw + gap));
        int count = m.gods.size() + (m.mayShapePantheon ? 1 : 0);
        int rows = (count + cols - 1) / cols;
        int off = ui.scrollBegin("pantheon", x, y, w, h);
        for (int i = 0; i < count; i++) {
            int cx = x + (i % cols) * (cw + gap), cy = y - off + (i / cols) * (ch + gap);
            boolean hot = ui.hover(cx, cy, cw, ch);
            if (i == m.gods.size()) {
                ui.panel(cx, cy, cw, ch, hot ? Ui.PANEL2 : Ui.PANEL);
                ui.frame(cx, cy, cw, ch, hot ? Ui.OK : Ui.LINE);
                ui.text("+", cx + cw / 2 - 3, cy + 18, Ui.OK);
                ui.small("New god", cx + (cw - ui.smallWidth("New god")) / 2, cy + 34, Ui.DIM);
                if (ui.clicked(cx, cy, cw, ch)) {
                    Ui.click();
                    m.go(GuiModel.CREATE, null);
                }
                continue;
            }
            God g = m.gods.get(i);
            ui.panel(cx, cy, cw, ch, hot ? Ui.HOVER : Ui.PANEL);
            ui.frame(cx, cy, cw, ch, hot ? 0xFF000000 | g.color() : Ui.LINE);
            ui.panel(cx, cy, cw, 2, 0xFF000000 | Pages.moodColor(g.mood()));
            Pages.head(s, g, cx + 7, cy + 9, 2);
            int tx = cx + 46;
            ui.text(ui.fit(g.label(), cw - 50), tx, cy + 8, 0xFF000000 | (g.name() == null ? 0x9A9AA8 : g.color()));
            ui.small(ui.fitSmall(g.epithet() == null ? "@" + g.id() : g.epithet(), cw - 50), tx, cy + 19, Ui.DIM);
            ui.small("● " + Pages.stateWord(g), tx, cy + 29, Pages.stateColor(g, ui.now));
            ui.small(g.mood() + " · " + g.power(), tx, cy + 38, 0xFF000000 | Pages.moodColor(g.mood()));
            if (g.wounded()) ui.bar(cx + 7, cy + 46, 32, 3, g.health() / g.max(), 0xE0203A);
            if (g.pending() > 0) {
                String p = g.pending() + " pending";
                ui.small(p, tx, cy + 48, Ui.WARN);
            }
            if (hot) ui.tip(Pages.card(g));
            if (ui.clicked(cx, cy, cw, ch)) {
                Ui.click();
                m.go(GuiModel.OVERVIEW, g.id());
            }
        }
        ui.scrollEnd("pantheon", x, y, w, h, rows * (ch + gap));
    }

    // ---- New god ----------------------------------------------------------------------------------

    static void create(ManagerScreen s, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        String made = m.take("create");
        if (made != null) {
            ui.set("c.name", "");
            m.go(GuiModel.OVERVIEW, made);
            return;
        }
        int cy = ui.heading("Name", x, y, w);
        ui.box("c.name", x, cy, 180, 16, "Leave empty: it names itself", 32);
        ui.small("One invented word, like Vorthak.", x + 186, cy + 5, Ui.FAINT);
        cy += 18;
        cy += Pages.fieldError(s, "create", "name", x, cy, w) + 4;

        cy = ui.heading("Nature", x, cy, w);
        String nature = s.pick.getOrDefault("c.nature", "random");
        List<Named> kinds = new ArrayList<>();
        kinds.add(new Named("random", "Any nature, chosen at its birth.", -1));
        kinds.addAll(m.catalog.archetypes());
        List<String> labels = new ArrayList<>();
        for (Named k : kinds) labels.add(Pages.title(k.key()));
        cy = s.flow(labels, x, cy, w, (i, px, py) -> {
            if (ui.chip(labels.get(i), px, py, kinds.get(i).key().equals(nature), kinds.get(i).color())) {
                s.pick.put("c.nature", kinds.get(i).key());
            }
            if (ui.hover(px, py, ui.chipWidth(labels.get(i)), 13)) ui.tip(List.of(kinds.get(i).text()));
        });
        for (Named k : kinds) {
            if (k.key().equals(nature)) cy += ui.wrapSmall(k.text(), x, cy, w, Ui.DIM, 2);
        }
        cy += Pages.fieldError(s, "create", "archetype", x, cy, w) + 5;

        cy = ui.heading("Power", x, cy, w);
        String power = s.pick.getOrDefault("c.power", "greater");
        int px = x;
        for (Named p : m.catalog.powers()) {
            String label = Pages.title(p.key()) + " god";
            if (ui.chip(label, px, cy, p.key().equals(power), -1)) s.pick.put("c.power", p.key());
            px += ui.chipWidth(label) + 3;
        }
        cy += 16;
        for (Named p : m.catalog.powers()) {
            if (p.key().equals(power)) cy += ui.wrapSmall(p.text(), x, cy, w, Ui.DIM, 3);
        }
        cy += Pages.fieldError(s, "create", "power", x, cy, w) + 8;

        boolean busy = m.busy("create");
        if (ui.button(busy ? "Creating…" : "Create this god", x, cy, 130, 18, Ui.PRIMARY, !busy) || ui.submitted("c.name")) {
            GuiClient.act("create", null, "create_god", null, "name", ui.value("c.name").trim(), "archetype", nature,
                    "power", power);
        }
        ui.small("It thinks on its own, so it costs about as much to run as the first god.", x + 136, cy + 6, Ui.FAINT);
        cy += 22;
        // Anything the server refused that belongs to no single field.
        Pages.fieldError(s, "create", null, x, cy, w);
    }

    // ---- Quick switches ---------------------------------------------------------------------------

    static void switches(ManagerScreen s, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        if (m.switches.isEmpty()) {
            Pages.waiting(ui, x, y);
            return;
        }
        ui.small("For every god on the server. Saved to config.yml at once.", x, y, Ui.FAINT);
        int top = y + 11, areaH = y + h - top;
        int off = ui.scrollBegin("switches", x, top, w, areaH);
        int cy = top - off;
        for (Switch sw : m.switches) {
            boolean hot = ui.hover(x, cy, w - 4, 20);
            if (hot) ui.panel(x, cy, w - 4, 20, Ui.PANEL);
            if (ui.toggle(x + 4, cy + 4, sw.on(), true)) GuiClient.act("sw", null, "switch", null, "key", sw.key());
            ui.text(sw.label(), x + 32, cy + 2, sw.on() ? Ui.TEXT : Ui.DIM);
            ui.small(ui.fitSmall(sw.help(), w - 44), x + 32, cy + 12, Ui.FAINT);
            if (hot) ui.tip(List.of(sw.help(), "§d" + sw.key()));
            cy += 21;
        }
        ui.scrollEnd("switches", x, top, w, areaH, m.switches.size() * 21);
        Pages.feedback(s, "sw", x + 270, y, w - 270, 1);
    }

    // ---- Config -----------------------------------------------------------------------------------

    static void config(ManagerScreen s, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        ui.box("cfg.search", x, y, 130, 15, "Search settings…", 48);
        String search = ui.value("cfg.search").trim();
        String tab = s.pick.get("cfg.tab");
        int tx = x + 134;
        List<String> tabs = new ArrayList<>();
        tabs.add("All");
        tabs.addAll(m.catalog.tabs());
        for (String t : tabs) {
            boolean on = t.equals("All") ? tab == null : t.equals(tab);
            if (ui.chip(t, tx, y + 1, on && search.isEmpty(), -1)) {
                if (t.equals("All")) s.pick.remove("cfg.tab");
                else s.pick.put("cfg.tab", t);
                ui.set("cfg.search", "");
                ui.scrollTo("config", 0);
            }
            tx += ui.chipWidth(t) + 2;
        }
        if (m.config.isEmpty()) {
            Pages.waiting(ui, x, y + 22);
            return;
        }
        // A search looks through every tab.
        List<Option> options = m.options(search.isEmpty() ? tab : null, search);
        int top = y + 19, areaH = y + h - top;
        int off = ui.scrollBegin("config", x, top, w, areaH);
        int cy = top - off;
        int start = cy;
        int control = 150, lw = w - control - 12;
        for (Option o : options) {
            String slot = "cfg." + o.key();
            String error = m.error(slot, o.key(), ui.now);
            int rh = 22 + (error == null ? 0 : ui.smallHeight(error, w - 12, 2) + 1);
            if (cy + rh >= top && cy <= top + areaH) {
                boolean hot = ui.hover(x, cy, w - 4, rh);
                if (hot) ui.panel(x, cy, w - 4, rh, Ui.PANEL);
                boolean changed = !o.value().equalsIgnoreCase(o.fallback());
                if (changed) ui.panel(x, cy + 2, 2, 17, Ui.ACCENT);
                ui.text(ui.fit(o.label(), lw), x + 6, cy + 2, o.locked() ? Ui.DIM : Ui.TEXT);
                ui.small(ui.fitSmall(o.help(), lw), x + 6, cy + 12, Ui.FAINT);
                if (hot && ui.mx < x + lw) {
                    ui.tip(List.of("§a" + o.label(), o.help(), "§d" + o.key(), "§dDefault: " + show(o, o.fallback())
                            + (o.restart() ? " · needs a restart" : " · takes hold at once")));
                }
                control(s, o, slot, x + w - control - 6, cy + 3, control);
                if (error != null) ui.wrapSmall(error, x + 6, cy + 21, w - 12, Ui.BAD, 2);
            }
            cy += rh;
        }
        if (options.isEmpty()) ui.small("No setting matches.", x, top + 2, Ui.FAINT);
        ui.scrollEnd("config", x, top, w, areaH, cy - start);
    }

    /** The control for one setting, right-aligned in `w` pixels. */
    private static void control(ManagerScreen s, Option o, String slot, int x, int y, int w) {
        Ui ui = s.ui;
        if (o.locked()) {
            String v = show(o, o.value());
            ui.small(v, x + w - ui.smallWidth(v), y + 1, Ui.DIM);
            ui.small("config.yml only", x + w - ui.smallWidth("config.yml only"), y + 9, Ui.FAINT);
            ui.tipIf(x, y, w, 16, "Needs a restart or opens a port: change it in config.yml.");
            return;
        }
        switch (o.type()) {
            case "bool" -> {
                boolean on = o.value().equalsIgnoreCase("true");
                ui.small(on ? "ON" : "OFF", x + w - 40, y + 3, on ? Ui.OK : Ui.DIM);
                if (ui.toggle(x + w - 22, y + 1, on, true)) set(o, slot, String.valueOf(!on));
            }
            case "choice" -> {
                List<String> c = o.choices();
                int i = c.indexOf(o.value().toLowerCase(Locale.ROOT));
                if (ui.button("‹", x, y, 14, 14) && !c.isEmpty()) set(o, slot, c.get(Math.floorMod(i - 1, c.size())));
                ui.panel(x + 16, y, w - 32, 14, 0xFF0D0D11);
                String v = ui.fit(o.value(), w - 36);
                ui.text(v, x + 16 + (w - 32 - ui.width(v)) / 2, y + 3, Ui.TEXT);
                if (ui.button("›", x + w - 14, y, 14, 14) && !c.isEmpty()) set(o, slot, c.get(Math.floorMod(i + 1, c.size())));
                if (ui.hover(x, y, w, 14)) ui.tip(List.of("§dOne of: " + String.join(", ", c)));
            }
            default -> {
                String id = "cfg.box." + o.key();
                int unit = o.unit().isEmpty() ? 0 : ui.smallWidth(o.unit()) + 4;
                int bw = w - 32 - unit;
                if (ui.button("−", x, y, 14, 14)) set(o, slot, step(o, -1));
                ui.box(id, x + 16, y, bw, 14, "", 16);
                // Show the server's value unless the operator is typing or their value was just refused.
                boolean refused = s.m.error(slot, o.key(), ui.now) != null;
                if (!ui.focused(id) && !s.m.busy(slot) && !refused && !ui.value(id).equals(o.value())) ui.set(id, o.value());
                if (ui.submitted(id)) set(o, slot, ui.value(id).trim());
                if (ui.button("+", x + 18 + bw, y, 14, 14)) set(o, slot, step(o, 1));
                if (unit > 0) ui.small(o.unit(), x + w - unit + 4, y + 4, Ui.DIM);
                if (ui.hover(x, y, w, 14)) ui.tip(List.of("§d" + trim(o.min()) + " to " + trim(o.max()) + (o.unit().isEmpty() ? "" : " " + o.unit())
                        + " · type a value and press Enter"));
            }
        }
    }

    private static void set(Option o, String slot, String value) {
        GuiClient.act(slot, null, "config_set", null, "key", o.key(), "value", value);
    }

    /** The value one step up or down, as text; the server decides whether it is allowed. */
    static String step(Option o, int direction) {
        double v;
        try {
            v = Double.parseDouble(o.value());
        } catch (NumberFormatException e) {
            v = o.min();
        }
        double next = Math.max(o.min(), Math.min(o.max(), v + direction * o.step()));
        return o.type().equals("int") ? String.valueOf(Math.round(next)) : String.valueOf(Math.round(next * 100) / 100.0);
    }

    static String show(Option o, String v) {
        if (o.type().equals("bool")) return v.equalsIgnoreCase("true") ? "ON" : "OFF";
        return o.unit().isEmpty() ? v : v + " " + o.unit();
    }

    static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ---- Costs ------------------------------------------------------------------------------------

    static void costs(ManagerScreen s, int x, int y, int w, int h) {
        Ui ui = s.ui;
        Costs c = s.m.costs;
        if (c == null) {
            Pages.waiting(ui, x, y);
            return;
        }
        if (c.off()) {
            ui.wrapSmall("Costs are hidden on this server (client-mod.show-costs: false in config.yml).", x, y, w, Ui.DIM, 2);
            return;
        }
        String[] names = { "Last minute", "Last hour", "Last 24 hours", "This session", "All time" };
        double[] values = { c.minute(), c.hour(), c.day(), c.session(), c.total() };
        int tw = (w - 16) / 5;
        for (int i = 0; i < 5; i++) {
            int tx = x + i * (tw + 4);
            ui.panel(tx, y, tw, 30, Ui.PANEL);
            ui.small(names[i], tx + 5, y + 4, Ui.DIM);
            ui.text(Pages.usd(values[i]) + (!c.complete() && !Double.isNaN(values[i]) ? "+" : ""), tx + 5, y + 15,
                    i == 4 ? Ui.WARN : Ui.TEXT);
        }
        int cy = y + 36;
        cy = ui.heading("Spend per minute, last hour", x, cy, w);
        double max = 0;
        for (double v : c.spark()) max = Math.max(max, v);
        int chartH = Math.max(24, Math.min(60, h - 150));
        ui.panel(x, cy, w, chartH, 0xFF0D0D11);
        int n = c.spark().length;
        if (n > 0 && max > 0) {
            float bw = w / (float) n;
            for (int i = 0; i < n; i++) {
                int bh = (int) Math.round((chartH - 4) * c.spark()[i] / max);
                int bx = x + Math.round(i * bw);
                if (bh > 0) ui.g.fill(bx + 1, cy + chartH - 1 - bh, x + Math.round((i + 1) * bw), cy + chartH - 1, Ui.ACCENT);
                if (ui.hover(bx, cy, Math.max(1, Math.round(bw)), chartH)) {
                    ui.tip(List.of((n - 1 - i) + " min ago: " + Pages.usd(c.spark()[i])));
                }
            }
            ui.small("peak " + Pages.usd(max) + "/min", x + 3, cy + 2, Ui.DIM);
        } else {
            ui.small("Nothing spent in the last hour.", x + 4, cy + chartH / 2 - 3, Ui.FAINT);
        }
        cy += chartH + 6;

        cy = ui.heading("By god", x, cy, w);
        int c1 = x + w - 190, c2 = x + w - 125, c3 = x + w - 60;
        ui.small("last hour", c1, cy, Ui.FAINT);
        ui.small("last 24 h", c2, cy, Ui.FAINT);
        ui.small("session", c3, cy, Ui.FAINT);
        cy += 9;
        for (CostGod g : c.gods()) {
            if (cy > y + h - 22) break;
            God god = s.m.god(g.id());
            ui.text(ui.fit(g.name() == null ? "Unborn god (@" + g.id() + ")" : g.name(), w - 200), x + 2, cy,
                    god == null || g.name() == null ? Ui.DIM : 0xFF000000 | god.color());
            ui.text(Pages.usd(g.hour()), c1, cy, Ui.TEXT);
            ui.text(Pages.usd(g.day()), c2, cy, Ui.TEXT);
            ui.text(Pages.usd(g.session()), c3, cy, Ui.TEXT);
            cy += 11;
        }
        String foot = Pages.count(c.requests()) + " requests this session · tokens: " + Pages.count(c.input()) + " in, "
                + Pages.count(c.cacheRead()) + " cache read, " + Pages.count(c.cacheWrite()) + " cache write, "
                + Pages.count(c.output()) + " out";
        ui.small(ui.fitSmall(foot, w), x, y + h - 17, Ui.DIM);
        ui.small(c.complete() ? "Estimated from the price table; your Anthropic bill is the truth."
                : "+ some requests used a model with no known price and are not counted.", x, y + h - 8, Ui.FAINT);
    }
}
