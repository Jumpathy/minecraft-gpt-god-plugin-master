package net.bigyous.gptgod.client.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.bigyous.gptgod.client.gui.GuiProtocol.Catalog;
import net.bigyous.gptgod.client.gui.GuiProtocol.Commands;
import net.bigyous.gptgod.client.gui.GuiProtocol.Config;
import net.bigyous.gptgod.client.gui.GuiProtocol.Costs;
import net.bigyous.gptgod.client.gui.GuiProtocol.God;
import net.bigyous.gptgod.client.gui.GuiProtocol.GodDetail;
import net.bigyous.gptgod.client.gui.GuiProtocol.Line;
import net.bigyous.gptgod.client.gui.GuiProtocol.Mortal;
import net.bigyous.gptgod.client.gui.GuiProtocol.Mortals;
import net.bigyous.gptgod.client.gui.GuiProtocol.Option;
import net.bigyous.gptgod.client.gui.GuiProtocol.Result;
import net.bigyous.gptgod.client.gui.GuiProtocol.Switch;

/**
 * Everything the God Manager screen knows: what the plugin last sent, where the operator is (with the
 * pages to go back through), the requests still waiting for an answer and the answers shown beside the
 * fields that caused them. Pure (no Minecraft), so navigation and feedback are unit tested.
 */
public final class GuiModel {
    /** The pages. A god's tabs are in {@link #GOD_TABS}; the rest are about the whole server. */
    public static final String PANTHEON = "pantheon", OVERVIEW = "overview", COMMANDS = "commands", MORTALS = "mortals",
            NATURE = "nature", BODY = "body", MIND = "mind", JOURNAL = "journal", CREATE = "create",
            SWITCHES = "switches", CONFIG = "config", COSTS = "costs";
    public static final List<String> GOD_TABS = List.of(OVERVIEW, COMMANDS, MORTALS, NATURE, BODY, MIND, JOURNAL);
    static final int JOURNAL_KEEP = 400;
    static final int HISTORY_KEEP = 50;
    static final int BACK_KEEP = 32;
    /** A request with no answer after this long is shown as failed. */
    static final long TIMEOUT_MS = 8000;

    /** Where the operator is: a page, and the god it is about (null on server pages). */
    public record Route(String page, String realm) {
        public boolean ofGod() {
            return GOD_TABS.contains(page);
        }
    }

    /**
     * The answer shown beside an action.
     *
     * @param field the input an error belongs to (null = the action itself)
     * @param needs "name" or "confirm" when the server wants a confirmation first
     */
    public record Feedback(boolean ok, String field, String text, String needs, long at) {
    }

    /** A request waiting for its result. slot names the place in the screen that shows the answer. */
    public record Waiting(String slot, String action, long since) {
    }

    public record Notice(String text, boolean error, long at) {
    }

    // ---- What the plugin sent --------------------------------------------------------------------

    public boolean several, showCosts, mayShapePantheon;
    public Catalog catalog = Catalog.EMPTY;
    public List<God> gods = List.of();
    public final Map<String, int[]> heads = new HashMap<>();
    public int headSize = 16;
    public final Map<String, GodDetail> details = new HashMap<>();
    public final Map<String, Commands> commands = new HashMap<>();
    public final Map<String, Mortals> mortals = new HashMap<>();
    public final Map<String, Mortal> mortal = new HashMap<>();
    public final Map<String, List<Line>> journal = new HashMap<>();
    public final Map<String, List<Option>> config = new LinkedHashMap<>();
    public List<Switch> switches = List.of();
    public Costs costs;

    // ---- Where the operator is --------------------------------------------------------------------

    private Route route = new Route(PANTHEON, null);
    private final Deque<Route> back = new ArrayDeque<>();
    private boolean visited;
    /** The mortal open on the Mortals tab, by god. */
    public final Map<String, String> openMortal = new HashMap<>();

    // ---- Requests and answers ---------------------------------------------------------------------

    private int nextId = 1;
    private final Map<String, Waiting> waiting = new HashMap<>();
    private final Map<String, Feedback> feedback = new HashMap<>();
    public final List<Notice> notices = new ArrayList<>();
    /** Commands typed before, oldest first. */
    public final List<String> history = new ArrayList<>();
    /** The last result that carried a value (a created god's id, a resolved player), by slot; taken once. */
    private final Map<String, String> values = new HashMap<>();

    /** Forgets everything the server told us (a new connection or a fresh open); keeps the command history. */
    public void reset() {
        catalog = Catalog.EMPTY;
        gods = List.of();
        heads.clear();
        details.clear();
        commands.clear();
        mortals.clear();
        mortal.clear();
        journal.clear();
        config.clear();
        switches = List.of();
        costs = null;
        waiting.clear();
        feedback.clear();
        values.clear();
        notices.clear();
        openMortal.clear();
        back.clear();
        route = new Route(PANTHEON, null);
        visited = false;
    }

    /** The server opened the screen: start on the god it names, or on the pantheon when there are several. */
    public void opened(GuiProtocol.Open o) {
        several = o.several();
        showCosts = o.costs();
        mayShapePantheon = o.pantheon();
        // Coming back to the screen returns to where the operator was.
        if (!visited) {
            visited = true;
            back.clear();
            route = o.several() ? new Route(PANTHEON, null) : new Route(OVERVIEW, o.realm());
        }
    }

    /** Takes one decoded message. Returns the Open when the screen should be shown, else null. */
    public GuiProtocol.Open accept(Object m, long now) {
        switch (m) {
            case GuiProtocol.Open o -> {
                opened(o);
                return o;
            }
            case Result r -> result(r, now);
            case GuiProtocol.Note n -> notice(n.text(), "error".equals(n.level()), now);
            case Catalog c -> catalog = c;
            case GuiProtocol.Pantheon p -> {
                gods = p.gods();
                // A god that is gone cannot stay open.
                if (route.realm() != null && god(route.realm()) == null && !gods.isEmpty()) {
                    back.removeIf(r -> r.realm() != null && god(r.realm()) == null);
                    route = new Route(PANTHEON, null);
                }
            }
            case GuiProtocol.Heads h -> {
                headSize = h.size();
                heads.putAll(h.pixels());
            }
            case GodDetail d -> details.put(d.realm(), d);
            case Commands c -> commands.put(c.realm(), c);
            case Mortals ms -> mortals.put(ms.realm(), ms);
            case Mortal one -> mortal.put(one.realm(), one);
            case GuiProtocol.Journal j -> journal(j);
            case Config c -> config.put(c.tab(), c.options());
            case GuiProtocol.Switches s -> switches = s.list();
            case Costs c -> costs = c;
            default -> {
            }
        }
        return null;
    }

    private void journal(GuiProtocol.Journal j) {
        if (j.realm() == null) return;
        List<Line> have = j.reset() ? new ArrayList<>() : new ArrayList<>(journal.getOrDefault(j.realm(), List.of()));
        long last = have.isEmpty() ? Long.MIN_VALUE : have.get(have.size() - 1).seq();
        for (Line l : j.lines()) {
            if (l.seq() > last) {
                have.add(l);
                last = l.seq();
            }
        }
        if (have.size() > JOURNAL_KEEP) have = new ArrayList<>(have.subList(have.size() - JOURNAL_KEEP, have.size()));
        journal.put(j.realm(), have);
    }

    public God god(String realm) {
        if (realm == null) return null;
        for (God g : gods) if (g.id().equals(realm)) return g;
        return null;
    }

    // ---- Navigation ------------------------------------------------------------------------------

    public Route route() {
        return route;
    }

    /** Goes to a page, remembering where we were so Back returns there. */
    public void go(String page, String realm) {
        Route next = new Route(page, GOD_TABS.contains(page) ? realm : null);
        if (next.equals(route)) return;
        back.push(route);
        while (back.size() > BACK_KEEP) back.removeLast();
        route = next;
    }

    /** Switches tab on the same god without growing the way back (tabs are siblings). */
    public void tab(String page) {
        if (!route.ofGod() || !GOD_TABS.contains(page)) {
            go(page, route.realm());
            return;
        }
        route = new Route(page, route.realm());
    }

    /** Goes back one page. False when there is nowhere to go back to: the screen closes. */
    public boolean back() {
        if (back.isEmpty()) return false;
        route = back.pop();
        return true;
    }

    public boolean canGoBack() {
        return !back.isEmpty();
    }

    /** The part of the plugin's state this page shows (what the screen subscribes to). */
    public static String part(String page) {
        return switch (page) {
            case OVERVIEW -> "god";
            case COMMANDS, NATURE, MIND -> "commands";
            case MORTALS -> "mortals";
            case BODY -> "looks";
            case JOURNAL -> "journal";
            case SWITCHES -> "switches";
            case CONFIG -> "config";
            case COSTS -> "costs";
            default -> "pantheon";
        };
    }

    // ---- Requests and feedback --------------------------------------------------------------------

    /** A fresh request id for a message that needs no feedback (subscriptions). */
    public String id() {
        return String.valueOf(nextId++);
    }

    /** Registers a request whose answer belongs at `slot`, and returns its id. */
    public String ask(String slot, String action, long now) {
        String id = id();
        waiting.put(id, new Waiting(slot, action, now));
        feedback.remove(slot);
        return id;
    }

    private void result(Result r, long now) {
        Waiting w = waiting.remove(r.id());
        String text = r.ok() ? r.message() : r.error() != null ? r.error() : "That did not work.";
        if (w == null) {
            if (text != null && !text.isBlank()) notice(text, !r.ok(), now);
            return;
        }
        feedback.put(w.slot(), new Feedback(r.ok(), r.field(), text, r.needs(), now));
        if (r.ok() && r.value() != null) values.put(w.slot(), r.value());
    }

    /** The answer for a slot, or null. Answers to requests that never came back turn into an error. */
    public Feedback feedback(String slot, long now) {
        for (Map.Entry<String, Waiting> e : waiting.entrySet()) {
            Waiting w = e.getValue();
            if (w.slot().equals(slot)) {
                if (now - w.since() < TIMEOUT_MS) return null;
                waiting.remove(e.getKey());
                Feedback f = new Feedback(false, null, "The server did not answer.", null, now);
                feedback.put(slot, f);
                return f;
            }
        }
        return feedback.get(slot);
    }

    /** The error for one input of a slot, or null. A slot's error with no field belongs to the action. */
    public String error(String slot, String field, long now) {
        Feedback f = feedback(slot, now);
        if (f == null || f.ok() || f.needs() != null) return null;
        return java.util.Objects.equals(f.field(), field) ? f.text() : null;
    }

    public boolean busy(String slot) {
        for (Waiting w : waiting.values()) if (w.slot().equals(slot)) return true;
        return false;
    }

    public void clear(String slot) {
        feedback.remove(slot);
    }

    /** The value a successful request brought back for this slot, once. */
    public String take(String slot) {
        return values.remove(slot);
    }

    public void notice(String text, boolean error, long now) {
        if (text == null || text.isBlank()) return;
        notices.add(new Notice(text, error, now));
        while (notices.size() > 20) notices.remove(0);
    }

    /** The newest notice younger than `ms`, or null. */
    public Notice latest(long now, long ms) {
        if (notices.isEmpty()) return null;
        Notice n = notices.get(notices.size() - 1);
        return now - n.at() <= ms ? n : null;
    }

    // ---- Command history --------------------------------------------------------------------------

    /** Remembers a command that was sent (no blanks, no repeat of the last one). */
    public void remember(String command) {
        String c = command == null ? "" : command.trim();
        if (c.isEmpty()) return;
        if (!history.isEmpty() && history.get(history.size() - 1).equals(c)) return;
        history.add(c);
        while (history.size() > HISTORY_KEEP) history.remove(0);
    }

    /**
     * Walks the history: from index `at` (history.size() = the line being typed) one step up (-1) or
     * down (+1). Returns the new index, clamped.
     */
    public int historyStep(int at, int step) {
        return Math.max(0, Math.min(history.size(), at + step));
    }

    // ---- Filters ---------------------------------------------------------------------------------

    /** Journal lines of a god that pass the kind switches and contain the filter text. */
    public List<Line> journalLines(String realm, String filter, java.util.Set<String> hiddenKinds) {
        String q = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<Line> out = new ArrayList<>();
        for (Line l : journal.getOrDefault(realm, List.of())) {
            if (hiddenKinds != null && hiddenKinds.contains(l.kind())) continue;
            if (!q.isEmpty() && !l.text().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(l);
        }
        return out;
    }

    /** Config options matching a search (label, key or help), in one tab or in all (tab null). */
    public List<Option> options(String tab, String search) {
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Option> out = new ArrayList<>();
        for (Map.Entry<String, List<Option>> e : config.entrySet()) {
            if (tab != null && !tab.equals(e.getKey())) continue;
            for (Option o : e.getValue()) {
                if (q.isEmpty() || o.label().toLowerCase(Locale.ROOT).contains(q)
                        || o.key().toLowerCase(Locale.ROOT).contains(q) || o.help().toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(o);
                }
            }
        }
        return out;
    }
}
