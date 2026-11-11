package net.bigyous.gptgod.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bigyous.gptgod.client.gui.GuiProtocol.Command;
import net.bigyous.gptgod.client.gui.GuiProtocol.Commands;
import net.bigyous.gptgod.client.gui.GuiProtocol.Dial;
import net.bigyous.gptgod.client.gui.GuiProtocol.DialEnds;
import net.bigyous.gptgod.client.gui.GuiProtocol.God;
import net.bigyous.gptgod.client.gui.GuiProtocol.GodDetail;
import net.bigyous.gptgod.client.gui.GuiProtocol.Line;
import net.bigyous.gptgod.client.gui.GuiProtocol.Named;
import net.bigyous.gptgod.client.gui.GuiProtocol.Preset;
import net.bigyous.gptgod.client.gui.GuiProtocol.Report;

/** One god's tabs: Overview, Commands, Nature, Body, Mind and Journal (Mortals has its own class). */
final class GodPages {
    private GodPages() {
    }

    static int kindColor(String kind) {
        return switch (kind) {
            case "thought" -> 0xFFC9A6FF;
            case "tool" -> 0xFF7FD4FF;
            case "speech" -> 0xFFFFE08A;
            case "error" -> Ui.BAD;
            case "cycle" -> Ui.FAINT;
            default -> Ui.DIM;
        };
    }

    static String kindWord(String kind) {
        return switch (kind) {
            case "thought" -> "thinks";
            case "tool" -> "acts";
            case "speech" -> "speaks";
            case "error" -> "error";
            case "cycle" -> "cycle";
            default -> "note";
        };
    }

    // ---- Overview ---------------------------------------------------------------------------------

    static void overview(ManagerScreen s, String realm, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        God g = m.god(realm);
        GodDetail d = m.details.get(realm);
        if (g == null) {
            Pages.waiting(ui, x, y);
            return;
        }
        int side = 108, lw = w - side - 10;
        Pages.head(s, g, x + 1, y + 1, 3);
        int tx = x + 58;
        ui.text(ui.fit(g.label(), lw - 58), tx, y, 0xFF000000 | (g.name() == null ? 0x9A9AA8 : g.color()));
        if (g.epithet() != null) ui.small(ui.fitSmall(g.epithet(), lw - 58), tx, y + 11, Ui.DIM);
        ui.small((g.archetype() == null ? "unborn" : g.archetype()) + " · " + g.power() + " god", tx, y + 20, Ui.DIM);
        ui.small("● " + Pages.stateWord(g), tx, y + 30, Pages.stateColor(g, ui.now));
        ui.small("mood: " + g.mood() + " · " + Pages.bodyWord(g.body()), tx, y + 39, 0xFF000000 | Pages.moodColor(g.mood()));

        int cy = ui.heading("Heart", x, y + 56, lw);
        for (int i = 0; i < 5; i++) {
            ui.small(Pages.FEELINGS[i], x, cy + 1, Ui.DIM);
            ui.bar(x + 36, cy + 1, lw - 60, 5, g.heart()[i] / 100f, Pages.FEELING_COLORS[i]);
            String v = String.valueOf(Math.round(g.heart()[i]));
            ui.small(v, x + lw - ui.smallWidth(v), cy + 1, Ui.DIM);
            cy += 8;
        }
        if (g.wounded()) {
            ui.small("Health", x, cy + 2, Ui.BAD);
            ui.bar(x + 36, cy + 2, lw - 60, 5, g.health() / g.max(), 0xE0203A);
            String v = Math.round(g.health()) + "/" + Math.round(g.max());
            ui.small(v, x + lw - ui.smallWidth(v), cy + 2, Ui.BAD);
            cy += 9;
        }
        cy += 3;
        Commands c = m.commands.get(realm);
        int pending = c == null ? g.pending() : c.pending().size();
        cy = ui.heading("Pending commands (" + pending + ")", x, cy, lw);
        if (c != null) {
            for (int i = 0; i < c.pending().size() && i < 2 && cy < y + h - 40; i++) {
                Command p = c.pending().get(i);
                ui.small(ui.fitSmall("#" + p.n() + " " + p.text(), lw - ui.smallWidth(p.status()) - 8), x, cy, Ui.TEXT);
                ui.small(p.status(), x + lw - ui.smallWidth(p.status()), cy, Ui.DIM);
                cy += 8;
            }
        }
        if (pending == 0) {
            ui.small("None: it owes you nothing.", x, cy, Ui.FAINT);
            cy += 8;
        }
        cy = ui.heading("Lately", x, cy + 3, lw);
        List<Line> lines = m.journal.getOrDefault(realm, List.of());
        int room = Math.max(0, (y + h - cy) / 8);
        for (int i = Math.max(0, lines.size() - room); i < lines.size(); i++) {
            Line l = lines.get(i);
            String tag = Pages.clock(l.time()).substring(0, 5) + " " + GodPages.kindWord(l.kind()) + " ";
            ui.small(tag, x, cy, kindColor(l.kind()));
            ui.small(ui.fitSmall(l.text(), lw - ui.smallWidth(tag)), x + ui.smallWidth(tag), cy, Ui.TEXT);
            cy += 8;
        }
        if (lines.isEmpty()) ui.small("Nothing yet.", x, cy, Ui.FAINT);

        // Quick actions.
        int bx = x + w - side, by = y;
        boolean paused = d != null && d.paused(), off = d != null ? d.dormant() : g.state().equals("off");
        if (ui.button("Wake now", bx, by, side, 16, Ui.PRIMARY, true)) GuiClient.act("ov", realm, "wake", null);
        ui.tipIf(bx, by, side, 16, "It looks at its world right now.");
        by += 19;
        if (ui.button(paused ? "Resume thoughts" : "Pause thoughts", bx, by, side, 16)) GuiClient.act("ov", realm, "pause", null);
        ui.tipIf(bx, by, side, 16, paused ? "Let it think again." : "Stop it thinking (powers and body still work).");
        by += 19;
        if (ui.button(off ? "Turn the god on" : "Turn the god off", bx, by, side, 16)) GuiClient.act("ov", realm, "dormant", null);
        ui.tipIf(bx, by, side, 16, off ? "It thinks and takes form again." : "No thoughts and no body until you turn it on. Its memory is kept.");
        by += 19;
        if (ui.button("Summon to me", bx, by, side, 16)) GuiClient.act("ov", realm, "summon", null);
        by += 19;
        if (ui.button("Vanish", bx, by, side, 16)) GuiClient.act("ov", realm, "vanish", null);
        by += 19;
        if (ui.button("Give a command…", bx, by, side, 16)) {
            s.m.tab(GuiModel.COMMANDS);
            s.focusLater("cmd");
        }
        by += 20;
        Pages.feedback(s, "ov", bx, by, side, 8);
    }

    // ---- Commands ---------------------------------------------------------------------------------

    static void commands(ManagerScreen s, String realm, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        ui.box("cmd", x, y, w - 52, 16, "Type a command it must obey, then Enter…", 500);
        boolean send = ui.button("Send", x + w - 48, y, 48, 16, Ui.PRIMARY, true) || ui.submitted("cmd");
        if (ui.focused("cmd") && (ui.keyUp || ui.keyDown) && !m.history.isEmpty()) {
            if (s.history < 0 || s.history > m.history.size()) s.history = m.history.size();
            s.history = m.historyStep(s.history, ui.keyUp ? -1 : 1);
            ui.set("cmd", s.history >= m.history.size() ? "" : m.history.get(s.history));
        }
        if (send) {
            String text = ui.value("cmd").trim();
            GuiClient.act("cmd", realm, "command", null, "text", text);
            if (!text.isEmpty()) {
                GuiClient.remember(text);
                ui.set("cmd", "");
            }
            s.history = -1;
            ui.focus("cmd");
        }
        int cy = y + 19;
        int fh = Pages.feedback(s, "cmd", x, cy, w, 3);
        if (fh == 0) ui.small("Only the god hears it. Up / Down: earlier commands.", x, cy, Ui.FAINT);
        cy += Math.max(8, fh) + 4;

        cy = ui.heading("One click", x, cy, w);
        List<Preset> presets = m.catalog.presets();
        List<String> names = new ArrayList<>();
        for (Preset p : presets) names.add(p.name());
        cy = s.flow(names, x, cy, w, (i, px, py) -> {
            Preset p = presets.get(i);
            int cw = ui.chipWidth(p.name());
            if (p.confirm()) {
                if (ui.confirm("preset." + p.id(), p.name(), px, py, cw, 13)) {
                    GuiClient.act("cmd", realm, "preset", "true", "id", p.id());
                }
            } else if (ui.chip(p.name(), px, py, false, -1)) {
                GuiClient.act("cmd", realm, "preset", null, "id", p.id());
            }
            if (ui.hover(px, py, cw, 13)) ui.tip(List.of("§dThe god must:", p.text()));
        }) + 2;

        Commands c = m.commands.get(realm);
        int listH = y + h - cy;
        int off = ui.scrollBegin("cmd.list", x, cy, w, listH);
        int ly = cy - off;
        int start = ly;
        ly = ui.heading("Pending (" + (c == null ? 0 : c.pending().size()) + ")", x, ly, w - 4);
        if (c == null || c.pending().isEmpty()) {
            ui.small("None: the god owes you nothing.", x, ly, Ui.FAINT);
            ly += 10;
        } else {
            for (Command p : c.pending()) {
                int th = ui.smallHeight(p.text(), w - 62, 3);
                int rh = Math.max(16, th + 12);
                ui.panel(x, ly, w - 4, rh - 2, Ui.PANEL);
                ui.text("#" + p.n(), x + 4, ly + 3, p.standing() ? Ui.WARN : Ui.ACCENT);
                ui.wrapSmall(p.text(), x + 30, ly + 3, w - 62, Ui.TEXT, 3);
                ui.small((p.standing() ? "standing · " : "") + p.status() + (p.issuer() == null ? "" : " · from " + p.issuer()),
                        x + 30, ly + 4 + th, Ui.DIM);
                if (ui.button("✕", x + w - 24, ly + 1, 16, 13)) {
                    GuiClient.act("cmd", realm, "cancel_command", null, "n", String.valueOf(p.n()));
                }
                ui.tipIf(x + w - 24, ly + 1, 16, 13, "Cancel command #" + p.n());
                ly += rh;
            }
        }
        ly = ui.heading("Reports", x, ly + 3, w - 4);
        if (c == null || c.reports().isEmpty()) {
            ui.small("When it finishes a command, its report appears here.", x, ly, Ui.FAINT);
            ly += 10;
        } else {
            for (int i = c.reports().size() - 1; i >= 0; i--) {
                Report r = c.reports().get(i);
                boolean bad = r.text().contains("impossible");
                String when = Pages.clock(r.time()).substring(0, 5) + " ";
                ui.small(when, x, ly, Ui.FAINT);
                ly += ui.wrapSmall(r.text(), x + ui.smallWidth(when), ly, w - 8 - ui.smallWidth(when), bad ? Ui.WARN : Ui.TEXT, 4) + 2;
            }
        }
        ui.scrollEnd("cmd.list", x, cy, w, listH, ly - start);
    }

    // ---- Nature -----------------------------------------------------------------------------------

    static void nature(ManagerScreen s, String realm, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        GodDetail d = m.details.get(realm);
        if (d == null) {
            Pages.waiting(ui, x, y);
            return;
        }
        int cy = ui.heading("Nature: the same god, fine-tuned", x, y, w);
        if (!d.born() || d.dials().isEmpty()) {
            ui.small("Not yet born: its nature is set when it first wakes.", x, cy, Ui.FAINT);
            cy += 12;
        } else {
            int col = (w - 10) / 2;
            int rows = (d.dials().size() + 1) / 2;
            for (int i = 0; i < d.dials().size(); i++) {
                Dial dial = d.dials().get(i);
                int dx = x + (i / rows) * (col + 10), dy = cy + (i % rows) * 14;
                String id = "dial." + realm + "." + dial.key();
                ui.small(Pages.title(dial.key()), dx, dy + 2, Ui.TEXT);
                Integer set = ui.slider(id, dx + 52, dy, col - 72, 0, 10, dial.value(), 0xB070FF, dial.base());
                int shown = ui.sliding(id, dial.value());
                ui.small(String.valueOf(shown), dx + col - 14, dy + 2, shown != dial.base() ? Ui.ACCENT : Ui.DIM);
                if (ui.hover(dx, dy, col, 12)) {
                    DialEnds e = m.catalog.dial(dial.key());
                    ui.tip(List.of("§a" + Pages.title(dial.key()) + " " + shown + "/10", "0: " + e.low(), "10: " + e.high(),
                            "§dIts archetype gave it " + dial.base() + " (the notch)."));
                }
                if (set != null) GuiClient.act("nature", realm, "dial", null, "key", dial.key(), "value", String.valueOf(set));
            }
            cy += rows * 14 + 2;
            if (ui.button("Back to its archetype", x, cy, 134, 14)) {
                GuiClient.act("nature", realm, "dial", null, "key", "reset", "value", "reset");
            }
            Pages.feedback(s, "nature", x + 140, cy + 3, w - 140, 2);
            cy += 20;
        }

        cy = ui.heading("Rebirth: a NEW god of another archetype", x, cy, w);
        ui.small("Its name, nature and every memory start fresh. This cannot be undone.", x, cy, Ui.WARN);
        cy += 10;
        List<Named> kinds = new ArrayList<>();
        kinds.add(new Named("random", "A god of any archetype.", -1));
        kinds.addAll(m.catalog.archetypes());
        String pick = s.pick.get("rebirth");
        List<String> labels = new ArrayList<>();
        for (Named k : kinds) labels.add(Pages.title(k.key()));
        cy = s.flow(labels, x, cy, w, (i, px, py) -> {
            Named k = kinds.get(i);
            boolean now = d.born() && k.key().equalsIgnoreCase(d.god().archetype());
            if (ui.chip(labels.get(i), px, py, k.key().equals(pick), k.color())) {
                if (k.key().equals(pick)) s.pick.remove("rebirth");
                else s.pick.put("rebirth", k.key());
                m.clear("rebirth");
            }
            if (ui.hover(px, py, ui.chipWidth(labels.get(i)), 13)) {
                ui.tip(now ? List.of(k.text(), "§dThis is the god now.") : List.of(k.text()));
            }
        });
        if (pick != null) {
            String name = d.born() && d.god().name() != null ? d.god().name() : realm;
            for (Named k : kinds) {
                if (k.key().equals(pick)) cy += ui.wrapSmall(k.text(), x, cy, w, Ui.DIM, 2) + 3;
            }
            ui.box("rebirth.name", x, cy, 150, 16, "Type " + name + " to confirm", 64);
            boolean go = ui.button("Rebirth as " + Pages.title(pick), x + 154, cy, 130, 16, Ui.DANGER, true)
                    || ui.submitted("rebirth.name");
            if (go) {
                GuiClient.act("rebirth", realm, "rebirth", ui.value("rebirth.name").trim(), "archetype", pick);
                ui.set("rebirth.name", "");
            }
            Pages.feedback(s, "rebirth", x, cy + 19, w, 3);
        }
    }

    // ---- Body -------------------------------------------------------------------------------------

    static void body(ManagerScreen s, String realm, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        GodDetail d = m.details.get(realm);
        if (d == null) {
            Pages.waiting(ui, x, y);
            return;
        }
        int fh = Pages.feedback(s, "body", x, y, w, 2);
        int top = y + (fh > 0 ? fh + 3 : 0);
        int areaH = y + h - top;
        int off = ui.scrollBegin("body", x, top, w, areaH);
        int cw = w - 6;
        int cy = top - off;
        int start = cy;

        cy = ui.heading("Mood (lasts " + d.moodMinutes() + " minutes; a binding command)", x, cy, cw);
        List<Named> moods = m.catalog.moods();
        List<String> moodNames = new ArrayList<>();
        for (Named mood : moods) moodNames.add(mood.key().equals("calm") ? "Calm the god" : "Make " + mood.key());
        cy = s.flow(moodNames, x, cy, cw, (i, px, py) -> {
            Named mood = moods.get(i);
            if (ui.chip(moodNames.get(i), px, py, mood.key().equals(d.god().mood()), mood.color())) {
                GuiClient.act("body", realm, "mood", null, "mood", mood.key());
            }
            if (ui.hover(px, py, ui.chipWidth(moodNames.get(i)), 13)) ui.tip(List.of(mood.text()));
        });
        String heart = d.heartLines().isEmpty() ? "Its heart is at peace." : "Its heart: " + String.join(", ", d.heartLines()) + ".";
        ui.small(ui.fitSmall(heart, cw - 96), x, cy + 3, Ui.DIM);
        // What moved it lately, the sky over its world and the memory that last came back to it.
        if (!d.awareLines().isEmpty() && ui.hover(x, cy, cw - 96, 14)) ui.tip(wrapped(d.awareLines(), 64));
        if (ui.button("Calm its heart", x + cw - 90, cy, 90, 14)) GuiClient.act("body", realm, "calm_heart", null);
        ui.tipIf(x + cw - 90, cy, 90, 14, "Let go of every lasting feeling it has built up.");
        cy += 20;

        cy = ui.heading("Presence" + (d.bodyOn() ? "" : " (bodies are off in Quick switches)"), x, cy, cw);
        String[][] acts = { { "Summon to me", "summon", "Its vessel appears beside you." },
                { "Fly to me", "fly_to", "It soars over the land to you." },
                { "Follow me", "follow", "Its vessel walks after you." },
                { "Vanish", "vanish", "Its body fades; it watches unseen." } };
        int bw = (cw - 9) / 4;
        for (int i = 0; i < acts.length; i++) {
            int bx = x + i * (bw + 3);
            if (ui.button(acts[i][0], bx, cy, bw, 15)) GuiClient.act("body", realm, acts[i][1], null);
            ui.tipIf(bx, cy, bw, 15, acts[i][2]);
        }
        cy += 18;
        boolean revealed = d.god().body().equals("true_form");
        String gazeTip = switch (d.gaze()) {
            case "kill" -> "Its true form unfolds above you. Whoever looks at it DIES, you too: look away.";
            case "smite" -> "Its true form unfolds above you. Whoever looks is hurled back and struck down (never killed).";
            default -> "Its true form unfolds above you. Whoever looks at it is blinded.";
        };
        if (d.trueFormAllowed() && d.bodyOn()) {
            if (ui.confirm("trueform", "Reveal true form: " + d.gaze(), x, cy, 150, 15)) {
                GuiClient.act("body", realm, "true_form", "true");
            }
            ui.tipIf(x, cy, 150, 15, gazeTip + " Rare: it has a cooldown.");
        } else {
            ui.button("True form is forbidden", x, cy, 150, 15, Ui.NORMAL, false);
        }
        if (ui.button("End true form", x + 153, cy, 100, 15, Ui.NORMAL, revealed)) GuiClient.act("body", realm, "end_true_form", null);
        cy += 21;

        String wears = d.lookSetting().equals("auto") ? "by its nature (" + d.god().look() + ")" : d.lookSetting();
        cy = ui.heading("Looks: now " + wears, x, cy, cw);
        List<Named> looks = m.catalog.looks();
        int cell = 58, cols = Math.max(1, cw / cell);
        for (int i = 0; i < looks.size(); i++) {
            Named look = looks.get(i);
            int lx = x + (i % cols) * cell, ly = cy + (i / cols) * 34;
            boolean on = look.key().equals(d.lookSetting());
            boolean hot = ui.hover(lx, ly, cell - 3, 31);
            ui.panel(lx, ly, cell - 3, 31, on ? Ui.HOVER : hot ? Ui.PANEL2 : Ui.PANEL);
            if (on) ui.frame(lx, ly, cell - 3, 31, Ui.ACCENT);
            int[] px = m.heads.get(look.key().equals("auto") ? d.god().look() : look.key());
            int hx = lx + (cell - 3 - 16) / 2;
            if (px != null) ui.image(px, m.headSize, hx, ly + 3, 1);
            else ui.panel(hx, ly + 3, 16, 16, 0xFF2A2A34);
            String name = look.key().equals("auto") ? "Auto" : Pages.title(look.key());
            String cut = ui.fitSmall(name, cell - 6);
            ui.small(cut, lx + (cell - 3 - ui.smallWidth(cut)) / 2, ly + 22, on ? 0xFFFFFFFF : Ui.DIM);
            if (hot) ui.tip(List.of("§a" + name, look.text(), on ? "§dIts body wears this now." : "§dClick: its body changes at once."));
            if (ui.clicked(lx, ly, cell - 3, 31)) {
                Ui.click();
                GuiClient.act("body", realm, "look", null, "look", look.key());
            }
        }
        cy += ((looks.size() + cols - 1) / cols) * 34 + 4;

        String engine = d.engine().equals("kokoro") ? "" : " (voices need voice.tts.engine: kokoro; now " + d.engine() + ")";
        cy = ui.heading("Voice" + engine, x, cy, cw);
        List<Named> voices = m.catalog.voices();
        List<String> voiceNames = new ArrayList<>();
        for (Named v : voices) voiceNames.add(v.key());
        cy = s.flow(voiceNames, x, cy, cw, (i, px, py) -> {
            Named v = voices.get(i);
            if (ui.chip(v.key(), px, py, v.key().equalsIgnoreCase(d.voice()), -1)) {
                GuiClient.act("body", realm, "voice", null, "voice", v.key());
            }
            if (ui.hover(px, py, ui.chipWidth(v.key()), 13)) ui.tip(List.of(v.text(), "§dClick: speak with it, and hear it."));
        }) + 2;
        ui.small("Effect", x, cy + 3, Ui.DIM);
        List<Named> effects = m.catalog.effects();
        List<String> effectNames = new ArrayList<>();
        for (Named e : effects) effectNames.add(e.key().equals("auto") ? "by its mood" : e.key());
        cy = s.flow(effectNames, x + 30, cy, cw - 30, (i, px, py) -> {
            Named e = effects.get(i);
            if (ui.chip(effectNames.get(i), px, py, e.key().equals(d.effect()), -1)) {
                GuiClient.act("body", realm, "voice_effect", null, "effect", e.key());
            }
            if (ui.hover(px, py, ui.chipWidth(effectNames.get(i)), 13)) ui.tip(List.of(e.text()));
        }) + 2;
        if (ui.button("Hear it now", x, cy, 90, 15, Ui.NORMAL, d.voiceOn())) GuiClient.act("body", realm, "voice_sample", null);
        ui.tipIf(x, cy, 90, 15, d.voiceOn() ? "The god says a line to you (needs Simple Voice Chat)."
                : "Voice is off (needs Simple Voice Chat and voice.enabled).");
        cy += 20;
        ui.scrollEnd("body", x, top, w, areaH, cy - start);
    }

    // ---- Mind -------------------------------------------------------------------------------------

    static void mind(ManagerScreen s, String realm, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        GodDetail d = m.details.get(realm);
        if (d == null) {
            Pages.waiting(ui, x, y);
            return;
        }
        int cy = ui.heading("Might", x, y, w);
        List<Named> powers = m.catalog.powers();
        int px = x;
        for (Named p : powers) {
            String label = Pages.title(p.key()) + " god";
            if (ui.chip(label, px, cy, p.key().equals(d.god().power()), -1)) GuiClient.act("mind", realm, "power", null, "power", p.key());
            if (ui.hover(px, cy, ui.chipWidth(label), 13)) ui.tip(List.of(p.text()));
            px += ui.chipWidth(label) + 3;
        }
        cy += 16;
        for (Named p : powers) {
            if (p.key().equals(d.god().power())) cy += ui.wrapSmall(p.text(), x, cy, w, Ui.DIM, 3);
        }
        cy = ui.heading("Mind (model)", x, cy + 5, w);
        List<String> models = new ArrayList<>();
        models.add("default");
        models.addAll(m.catalog.models());
        List<String> modelNames = new ArrayList<>();
        for (String id : models) modelNames.add(id.equals("default") ? "Server default (" + Pages.model(d.configModel()) + ")" : Pages.model(id));
        cy = s.flow(modelNames, x, cy, w, (i, cx, cyy) -> {
            String id = models.get(i);
            boolean on = id.equals("default") ? d.model() == null : id.equals(d.model());
            if (ui.chip(modelNames.get(i), cx, cyy, on, -1)) GuiClient.act("mind", realm, "model", null, "model", id);
        });
        cy = ui.heading("Effort", x, cy + 3, w);
        List<String> efforts = new ArrayList<>();
        efforts.add("default");
        efforts.addAll(m.catalog.efforts());
        List<String> effortNames = new ArrayList<>();
        for (String e : efforts) effortNames.add(e.equals("default") ? "Server default (" + d.configEffort() + ")" : e);
        cy = s.flow(effortNames, x, cy, w, (i, cx, cyy) -> {
            String e = efforts.get(i);
            boolean on = e.equals("default") ? d.effort() == null : e.equals(d.effort());
            if (ui.chip(effortNames.get(i), cx, cyy, on, -1)) GuiClient.act("mind", realm, "effort", null, "effort", e);
            if (ui.hover(cx, cyy, ui.chipWidth(effortNames.get(i)), 13)) {
                ui.tip(List.of(switch (e) {
                    case "auto" -> "Harder when spoken to, lighter for idle looks.";
                    case "low" -> "Fastest replies.";
                    case "medium" -> "More thought per reply.";
                    case "high" -> "Deepest thought, slowest.";
                    default -> "Follows Server › Config.";
                }));
            }
        });
        cy += Pages.feedback(s, "mind", x, cy, w, 2) + 4;

        boolean removable = d.god().removable();
        if (!removable && !d.born()) return;
        String name = d.born() && d.god().name() != null ? d.god().name() : realm;
        cy = ui.heading(removable ? "Remove this god" : "Erase this god", x, cy + 4, w);
        cy += ui.wrapSmall(removable ? "Its thoughts stop and its body vanishes. Its memory is kept on the server in god/removed/."
                : "Its name, nature and every memory are wiped; a new god is born in its place.", x, cy, w, Ui.WARN, 2) + 3;
        ui.box("end.name", x, cy, 150, 16, "Type " + name + " to confirm", 64);
        boolean go = ui.button(removable ? "Remove " + name : "Erase " + name, x + 154, cy, 130, 16, Ui.DANGER, true)
                || ui.submitted("end.name");
        if (go) {
            if (removable) GuiClient.act("end", realm, "remove_god", ui.value("end.name").trim());
            else GuiClient.act("end", realm, "rebirth", ui.value("end.name").trim(), "archetype", "random");
            ui.set("end.name", "");
        }
        Pages.feedback(s, "end", x, cy + 19, w, 3);
    }

    /** Lines broken at spaces to at most {@code width} characters, for a tooltip. */
    static List<String> wrapped(List<String> lines, int width) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            StringBuilder row = new StringBuilder();
            for (String word : line.split(" ")) {
                if (row.length() > 0 && row.length() + 1 + word.length() > width) {
                    out.add(row.toString());
                    row.setLength(0);
                }
                if (row.length() > 0) row.append(' ');
                row.append(word);
            }
            if (row.length() > 0) out.add(row.toString());
        }
        return out;
    }

    // ---- Journal ----------------------------------------------------------------------------------

    static final String[] KINDS = { "thought", "tool", "speech", "error", "info", "cycle" };

    static void journal(ManagerScreen s, String realm, int x, int y, int w, int h) {
        Ui ui = s.ui;
        GuiModel m = s.m;
        ui.box("j.filter", x, y, 120, 15, "Filter…", 64);
        int kx = x + 124;
        Set<String> hidden = new HashSet<>();
        for (String k : KINDS) {
            boolean off = s.flags.contains("j.hide." + k);
            if (off) hidden.add(k);
            String label = kindWord(k);
            if (ui.chip(label, kx, y + 1, !off, kindColor(k) & 0xFFFFFF)) {
                if (off) s.flags.remove("j.hide." + k);
                else s.flags.add("j.hide." + k);
            }
            kx += ui.chipWidth(label) + 3;
        }
        List<Line> lines = m.journalLines(realm, ui.value("j.filter"), hidden);
        int top = y + 19, areaH = y + h - top;
        int tw = w - 70;
        int total = 0;
        int[] heights = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            heights[i] = Math.max(8, ui.smallHeight(lines.get(i).text(), tw, 12)) + 2;
            total += heights[i];
        }
        // Stay at the newest line unless the operator scrolled up.
        String key = "j.total." + realm;
        int before = Integer.parseInt(s.pick.getOrDefault(key, "0"));
        boolean atEnd = ui.scrolled("journal") >= before - areaH - 4;
        if (total != before) {
            s.pick.put(key, String.valueOf(total));
            if (atEnd) ui.scrollTo("journal", Math.max(0, total - areaH));
        }
        int off = ui.scrollBegin("journal", x, top, w, areaH);
        int ly = top - off;
        for (int i = 0; i < lines.size(); i++) {
            if (ly + heights[i] >= top && ly <= top + areaH) {
                Line l = lines.get(i);
                ui.small(Pages.clock(l.time()), x, ly, Ui.FAINT);
                ui.small(kindWord(l.kind()), x + 34, ly, kindColor(l.kind()));
                ui.wrapSmall(l.text(), x + 64, ly, tw, l.kind().equals("error") ? Ui.BAD : Ui.TEXT, 12);
            }
            ly += heights[i];
        }
        if (lines.isEmpty()) ui.small("Nothing to show yet.", x, top + 2, Ui.FAINT);
        ui.scrollEnd("journal", x, top, w, areaH, total);
    }
}
