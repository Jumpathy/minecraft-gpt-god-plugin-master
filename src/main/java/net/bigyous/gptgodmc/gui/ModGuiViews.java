package net.bigyous.gptgodmc.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModGuiMessages;
import net.bigyous.gptgodmc.client.ModGuiMessages.Catalog;
import net.bigyous.gptgodmc.client.ModGuiMessages.Command;
import net.bigyous.gptgodmc.client.ModGuiMessages.CostGod;
import net.bigyous.gptgodmc.client.ModGuiMessages.CostView;
import net.bigyous.gptgodmc.client.ModGuiMessages.DialEnds;
import net.bigyous.gptgodmc.client.ModGuiMessages.GodDetail;
import net.bigyous.gptgodmc.client.ModGuiMessages.GodRow;
import net.bigyous.gptgodmc.client.ModGuiMessages.JournalLine;
import net.bigyous.gptgodmc.client.ModGuiMessages.MortalDetail;
import net.bigyous.gptgodmc.client.ModGuiMessages.MortalRow;
import net.bigyous.gptgodmc.client.ModGuiMessages.Named;
import net.bigyous.gptgodmc.client.ModGuiMessages.Report;
import net.bigyous.gptgodmc.client.ModMessages;
import net.bigyous.gptgodmc.god.ClientViews;
import net.bigyous.gptgodmc.god.Costs;
import net.bigyous.gptgodmc.god.Edicts;
import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.TabList;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.utils.TokenTracker;

/**
 * What the client mod's God Manager screen is shown: the same state the chest menu draws
 * ({@link GodManager#snapshot}, {@link GodManager#mortals}, {@link Advanced}, {@link MenuLayout}), as
 * messages. Server thread only.
 */
public final class ModGuiViews {
    private ModGuiViews() {
    }

    public static final int MORTALS_PER_PAGE = 50;

    /** The look a god's body wears now ("auto" resolved by its name and archetype). */
    public static String lookOf(Realm r) {
        String wanted = r.lookSetting();
        if (wanted != null && VesselLook.NAMES.contains(wanted)) return wanted;
        Persona p = r.persona();
        String hint = p == null ? null : (p.epithet == null ? "" : p.epithet) + " " + (p.name == null ? "" : p.name);
        return VesselLook.autoName(p == null ? r.archetype : p.archetype, hint);
    }

    static GodRow row(Realm r) {
        Persona p = r.persona();
        ModMessages.GodView v = ClientViews.god(r, null);
        boolean awake = r.get(net.bigyous.gptgodmc.god.God::isAwake);
        boolean paused = r.get(net.bigyous.gptgodmc.god.God::isPaused);
        String state = Godslayer.isSlain(r) ? "slain" : r.dormant() ? "off" : !awake ? "asleep" : paused ? "paused"
                : "awake";
        Vessel body = r.get(net.bigyous.gptgodmc.god.God::vessel);
        String form = body == null ? "formless" : body.inTrueForm() ? "true_form"
                : body.hasBody() || body.isMaterializing() ? "walks" : "formless";
        int pending = r.get(() -> Edicts.book().pending().size());
        return new GodRow(r.id, p == null ? null : p.shortName(), p == null ? null : p.epithet,
                p == null ? r.archetype : p.archetype, v.color(), r.power().id, state, v.thinking(), v.mood(),
                new double[] { v.wrath(), v.joy(), v.sorrow(), v.dread(), v.glory() }, form, pending, v.health(),
                v.maxHealth(), lookOf(r), net.bigyous.gptgodmc.god.Pantheon.created(r.id));
    }

    public static JsonObject pantheon() {
        List<GodRow> rows = new ArrayList<>();
        for (Realm r : Realm.all()) rows.add(row(r));
        return ModGuiMessages.pantheon(rows);
    }

    public static JsonObject god(Realm r) {
        MenuModel.State s = r.get(() -> GodManager.snapshot(r));
        MenuModel.Gods g = s.pantheon();
        MenuModel.VoiceNow voice = s.voice() == null ? new MenuModel.VoiceNow("kokoro", "am_michael", "auto") : s.voice();
        List<ModGuiMessages.Dial> dials = new ArrayList<>();
        for (MenuModel.Dial d : s.dials() == null ? List.<MenuModel.Dial>of() : s.dials()) {
            dials.add(new ModGuiMessages.Dial(d.key(), d.value(), d.base()));
        }
        return ModGuiMessages.god(new GodDetail(row(r), s.born(), s.awake(), s.paused(), s.setting("god.dormant", false),
                s.heart() == null ? List.of() : s.heart(), g.model(), g.effort(), g.configModel(), g.configEffort(),
                s.look() == null ? "auto" : s.look(), voice.voice(), voice.effect() == null ? "auto" : voice.effect(),
                voice.engine(), net.bigyous.gptgodmc.voice.VoiceModule.isActive(), MenuLayout.gaze(s),
                s.setting("vessel.enabled", true), s.setting("vessel.true-form.enabled", true), dials,
                Edicts.moodMinutes(), s.aware() == null ? List.of() : s.aware()));
    }

    public static JsonObject commands(Realm r) {
        return r.get(() -> {
            MenuModel.State s = GodManager.snapshot(r);
            Map<Integer, String> issuers = new java.util.HashMap<>();
            for (Edicts.Edict e : Edicts.book().pending()) issuers.put(e.number, e.issuer);
            List<Command> pending = new ArrayList<>();
            for (MenuModel.Pending p : s.pending()) {
                pending.add(new Command(p.number(), p.text(), p.standing(), p.status(), issuers.get(p.number())));
            }
            List<Report> reports = new ArrayList<>();
            for (Journal.Entry e : Journal.last(1500)) {
                if (e.kind() == Journal.Kind.INFO && isReport(e.text())) {
                    reports.add(new Report(e.seq(), e.time(), e.text()));
                }
            }
            if (reports.size() > 12) reports = new ArrayList<>(reports.subList(reports.size() - 12, reports.size()));
            return ModGuiMessages.commands(r.id, pending, reports);
        });
    }

    /** Journal lines that tell what became of a command: "Command #4 done: ...", "... impossible: ...", "... cancelled ...". */
    static boolean isReport(String text) {
        return text != null && text.startsWith("Command #");
    }

    /** A page of the mortals whose name contains the query (online first, as the chest menu orders them). */
    public static JsonObject mortals(Realm r, String query, int page) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<MenuModel.Mortal> all = r.get(() -> GodManager.mortals(r));
        List<MenuModel.Mortal> hits = new ArrayList<>();
        for (MenuModel.Mortal m : all) {
            if (q.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(q)
                    || (m.title() != null && m.title().toLowerCase(Locale.ROOT).contains(q))) {
                hits.add(m);
            }
        }
        int pages = Math.max(1, (hits.size() + MORTALS_PER_PAGE - 1) / MORTALS_PER_PAGE);
        int at = Math.max(0, Math.min(page, pages - 1));
        List<MortalRow> rows = new ArrayList<>();
        for (int i = at * MORTALS_PER_PAGE; i < hits.size() && rows.size() < MORTALS_PER_PAGE; i++) {
            MenuModel.Mortal m = hits.get(i);
            rows.add(new MortalRow(m.name(), m.favor(), Memory.favorLabel(m.favor()), m.online(), m.title(), m.seen(),
                    m.notes().size(), m.deeds().size()));
        }
        return ModGuiMessages.mortals(r.id, q, at, pages, hits.size(), rows);
    }

    public static JsonObject mortal(Realm r, String name) {
        MenuModel.Mortal m = name == null || name.isBlank() ? null : r.get(() -> GodManager.mortal(name, r));
        if (m == null) return ModGuiMessages.mortal(r.id, null);
        return ModGuiMessages.mortal(r.id, new MortalDetail(m.name(), m.favor(), Memory.favorLabel(m.favor()),
                m.online(), m.title(), m.summary(), m.offerings(), m.seen(), entries(m.notes()), entries(m.deeds())));
    }

    private static List<ModGuiMessages.Entry> entries(List<MenuModel.Entry> list) {
        List<ModGuiMessages.Entry> out = new ArrayList<>();
        for (MenuModel.Entry e : list) out.add(new ModGuiMessages.Entry(e.at(), e.text(), e.age()));
        return out;
    }

    /**
     * Journal lines of this god after `since`, oldest first, at most `limit`: thoughts, acts, speech,
     * errors and notes. Never perceptions (the raw text it sees stays in the mind viewer).
     */
    public static List<JournalLine> journal(Realm r, long since, int limit) {
        List<Journal.Entry> got = r.get(() -> since <= 0 ? Journal.last(limit * 2) : Journal.firstAfter(since, limit * 2));
        List<JournalLine> out = new ArrayList<>();
        for (Journal.Entry e : got) {
            if (e.kind() == Journal.Kind.PERCEPTION) continue;
            out.add(new JournalLine(e.seq(), e.time(), e.kind().id(), e.text()));
        }
        if (out.size() > limit) {
            // A fresh view keeps the newest; a follow-up keeps the oldest so nothing is skipped.
            return since <= 0 ? new ArrayList<>(out.subList(out.size() - limit, out.size()))
                    : new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /** Settings the screen shows but does not change: they need a restart or open a network port. */
    public static boolean locked(Advanced.Option o) {
        return o.restart() || o.key().endsWith(".port");
    }

    public static JsonObject config(String tab) {
        List<ModGuiMessages.Option> out = new ArrayList<>();
        for (Advanced.Option o : Advanced.of(tab)) {
            Object v = GPTGOD.get().getConfig().get(o.key());
            out.add(option(o, v == null ? o.fallback() : String.valueOf(v)));
        }
        return ModGuiMessages.config(tab, out);
    }

    static ModGuiMessages.Option option(Advanced.Option o, String value) {
        return new ModGuiMessages.Option(o.key(), o.label(), o.type().name().toLowerCase(Locale.ROOT), o.min(), o.max(),
                o.step(), o.choices(), o.fallback(), o.unit(), o.help(), o.restart(), locked(o), value);
    }

    public static JsonObject switches() {
        List<ModGuiMessages.Switch> out = new ArrayList<>();
        for (MenuModel.Setting s : MenuLayout.SETTINGS) {
            out.add(new ModGuiMessages.Switch(s.key(), s.label(), s.description(),
                    GPTGOD.get().getConfig().getBoolean(s.key(), s.fallback())));
        }
        return ModGuiMessages.switches(out);
    }

    /** allowed false = this operator may not see costs (client-mod.show-costs is off). */
    public static JsonObject costs(boolean allowed) {
        if (!allowed) return ModGuiMessages.costs(null);
        long now = System.currentTimeMillis();
        List<CostGod> gods = new ArrayList<>();
        for (Realm r : Realm.all()) {
            Persona p = r.persona();
            gods.add(new CostGod(r.id, p == null ? null : p.shortName(), Costs.window(r.id, Costs.HOUR, now),
                    Costs.window(r.id, Costs.DAY, now), Costs.session(r.id)));
        }
        return ModGuiMessages.costs(new CostView(Costs.window(null, Costs.MINUTE, now),
                Costs.window(null, Costs.HOUR, now), Costs.window(null, Costs.DAY, now), Costs.session(null),
                Costs.allTime(), TokenTracker.isCostComplete(), TokenTracker.getApiCallCount(),
                TokenTracker.getTotalInputTokens(), TokenTracker.getTotalOutputTokens(),
                TokenTracker.getCacheReadTokens(), TokenTracker.getCacheWriteTokens(), gods,
                Costs.perMinute(60, now)));
    }

    /** The lists every page chooses from. Sent once when the screen opens. */
    public static JsonObject catalog() {
        List<Named> archetypes = new ArrayList<>();
        Persona.archetypes().forEach((key, section) -> archetypes.add(new Named(key,
                section.getString("description", ""), TabList.archetypeColor(key).value())));
        return ModGuiMessages.catalog(catalog(archetypes));
    }

    /** The catalog but for the archetypes, which come from config.yml. Pure. */
    static Catalog catalog(List<Named> archetypes) {
        List<Named> looks = new ArrayList<>();
        for (String l : MenuLayout.lookOrder()) looks.add(new Named(l, MenuLayout.lookText(l), null));
        List<Named> voices = new ArrayList<>();
        new TreeMap<>(MenuLayout.VOICE_SLOTS).values().forEach(v -> voices.add(new Named(v[0], v[1], null)));
        List<Named> effects = new ArrayList<>();
        new TreeMap<>(MenuLayout.EFFECT_SLOTS).values().forEach(e -> effects.add(new Named(e,
                e.equals("auto") ? "Follows its mood: demonic in wrath, commanding when ominous."
                        : MenuLayout.effectText(e), null)));
        List<ModGuiMessages.Preset> presets = new ArrayList<>();
        for (MenuModel.Preset p : MenuLayout.PRESETS) {
            presets.add(new ModGuiMessages.Preset(p.id(), p.name(), p.command(), p.confirm()));
        }
        List<Named> moods = new ArrayList<>();
        for (VesselMood m : MenuLayout.MOOD_SLOTS.keySet()) {
            moods.add(new Named(m.id(), m == VesselMood.CALM ? "Lifts any mood you imposed: it acts by its own "
                    + "nature again." : "For a while its body shows it and it must let the mood colour every word "
                            + "and act.",
                    m == VesselMood.CALM ? MenuLayout.GREEN : TabList.nameColor(null, m).value()));
        }
        List<Named> powers = new ArrayList<>();
        for (String p : List.of("lesser", "greater", "supreme")) powers.add(new Named(p, MenuLayout.powerText(p), null));
        List<DialEnds> dials = new ArrayList<>();
        for (String key : Persona.DIALS) {
            String[] ends = MenuLayout.DIAL_ENDS.getOrDefault(key, new String[] { "Low.", "High." });
            dials.add(new DialEnds(key, ends[0], ends[1]));
        }
        return new Catalog(archetypes, looks, voices, effects, presets, moods, powers, MenuLayout.MODELS,
                List.of("auto", "low", "medium", "high"), dials, Advanced.TABS);
    }
}
