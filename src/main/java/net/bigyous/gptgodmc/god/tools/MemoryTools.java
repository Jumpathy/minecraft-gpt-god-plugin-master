package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Annals;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.QuestBoard;
import net.bigyous.gptgodmc.god.Realm;

/** The god's ledger and quests: what makes it consistent over time. */
final class MemoryTools {
    /** recall and search_annals answer at most this much text. */
    static final int MAX_OUTPUT = 6000;
    static final int MAX_LINES = 40;

    private MemoryTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("remember",
                        "Write a note in your ledger so you recall it in future thoughts: promises you made, "
                                + "threats, a mortal's ambitions, grudges, debts, what a place means to you. Notes "
                                + "appear in every perception. Use 'world' as the subject for notes about the world. "
                                + "With kind 'deed', record something a mortal did as a dated deed in their record.",
                        Schema.object()
                                .str("about", "A mortal's name, or 'world'.", true)
                                .str("note", "The note, one sentence.", true)
                                .choice("kind", "note (default): your own thought about them. deed: something they "
                                        + "did, kept with its date among their deeds.", false, List.of("note", "deed")),
                        a -> {
                            String about = a.str("about");
                            if (!"world".equalsIgnoreCase(about)) {
                                about = canonicalName(about);
                            }
                            if ("deed".equalsIgnoreCase(a.str("kind", "note"))) {
                                Memory.get().addDeed(about, a.str("note"));
                                return ToolResult.ok("Recorded as a deed of %s.", about);
                            }
                            Memory.get().remember(about, a.str("note"));
                            return ToolResult.ok("Noted about %s.", about);
                        }),
                GodTool.sync("judge",
                        "Change a mortal's favor with you (from -10 despised to +10 chosen) and record why. Judge "
                                + "after meaningful deeds — devotion, defiance, kindness, cruelty, completing or "
                                + "failing your quests. Favor guides how generous or harsh you are with them.",
                        Schema.object()
                                .str("player", "The mortal's name.", true)
                                .integer("change", "How much favor to add (negative to remove).", true, -5, 5)
                                .str("reason", "Why, one short sentence.", true),
                        a -> {
                            String name = canonicalName(a.str("player"));
                            int favor = Memory.get().adjustFavor(name, a.integer("change", 0, -5, 5), a.str("reason"));
                            return ToolResult.ok("%s's favor is now %+d (%s).", name, favor, Memory.favorLabel(favor));
                        }),
                GodTool.sync("set_quest",
                        "Give a mortal (or everyone) a divine quest. It appears on their scoreboard with a timer. "
                                + "It must be a real Minecraft action with a result you can check: build, mine or "
                                + "craft something, bring or offer items, slay a kind or number of mobs, tame or "
                                + "breed or feed an animal, plant or harvest, reach a place, sleep, fish, trade. "
                                + "Name what and how many. Never ask for what the game cannot do or you cannot see "
                                + "(petting, hugging, smiling, apologising sincerely, feelings); a cat is tamed or "
                                + "fed fish, not petted. Then watch whether they do it and resolve it with "
                                + "resolve_quest: reward success; neglect may simply lapse.",
                        Schema.object()
                                .str("for", "A mortal's name, or 'all'.", true)
                                .str("title", "Short quest title shown on the scoreboard (max ~32 characters).", true)
                                .str("details", "What exactly counts as success and how you will know it is done: the event or "
                                        + "thing you will look for (items offered, a structure, a death, a tamed animal).", false)
                                .integer("minutes", "Time allowed. Default from config.", false, 1, 120),
                        a -> {
                            String assignee = a.str("for");
                            if (!assignee.equalsIgnoreCase("all") && !assignee.equalsIgnoreCase("everyone")) {
                                assignee = Targets.player(assignee).getName();
                            } else {
                                assignee = "all";
                            }
                            int gap = GodSettings.questGapMinutes();
                            if (gap > 0 && net.bigyous.gptgodmc.god.Edicts.book().pending().stream()
                                    .noneMatch(e -> !e.standing())) {
                                long now = System.currentTimeMillis();
                                long last = Memory.get().lastQuestFor(assignee);
                                if (last > 0 && now - last < gap * 60_000L) {
                                    throw new ToolException("You gave " + (assignee.equals("all") ? "the mortals"
                                            : assignee) + " a quest " + Math.max(1, (now - last) / 60_000L)
                                            + " minutes ago. Let them be for now; do something of your own instead.");
                                }
                            }
                            String title = a.str("title");
                            if (title.length() > 40) {
                                title = title.substring(0, 40);
                            }
                            Memory.Quest q = Memory.get().addQuest(title, a.str("details", ""), assignee,
                                    a.integer("minutes", GodSettings.questMinutes(), 1, 120));
                            QuestBoard.refresh();
                            QuestBoard.announceNew(q);
                            return ToolResult.ok("Quest %s given to %s: \"%s\".", q.id, assignee, q.title);
                        }),
                GodTool.sync("resolve_quest",
                        "Close a quest as completed or failed once you have judged it. Follow up with a reward "
                                + "or punishment.",
                        Schema.object()
                                .str("quest", "Quest id (like q3) or its exact title.", true)
                                .choice("outcome", "How it ended.", true, List.of("completed", "failed"))
                                .str("note", "What happened, briefly.", false),
                        a -> {
                            Memory.Quest q = Memory.get().findQuest(a.str("quest"));
                            if (q == null) {
                                return ToolResult.error("No quest '" + a.str("quest") + "'. Check the quest list.");
                            }
                            if (!q.isActive()) {
                                return ToolResult.error("Quest " + q.id + " is already " + q.status + ".");
                            }
                            q.status = a.str("outcome").toLowerCase(Locale.ROOT);
                            q.outcome = a.str("note", null);
                            q.resolvedAt = System.currentTimeMillis();
                            Memory.get().questEnded(q);
                            QuestBoard.refresh();
                            QuestBoard.announceResolved(q);
                            return ToolResult.ok("Quest %s marked %s.", q.id, q.status);
                        }),
                GodTool.async("recall",
                        "Bring up everything you remember about one mortal, online or not: favor, title, notes, "
                                + "the summary of your past with them, all their dated deeds, quests, offerings, "
                                + "your last words with them, and the latest lines about them from your annals. "
                                + "Use it before answering someone whose history matters, or who is not in this "
                                + "perception's dossiers.",
                        Schema.object().str("player",
                                "The mortal's name (exact, or the start of it), or 'world' for your notes on the world.",
                                true),
                        MemoryTools::recall),
                GodTool.async("search_annals",
                        "Search your annals: the complete, permanent record of everything mortals did and said in "
                                + "your world and everything you did, newest first. Use it to check what really "
                                + "happened (who burned the chapel, what you promised someone, when someone last "
                                + "prayed). Every word of the query must appear; combine with player, kind and hours "
                                + "to narrow it.",
                        Schema.object()
                                .str("query", "Words that must all appear (any order, any case), e.g. 'chapel fire' "
                                        + "or 'promise'. Leave out to list everything that matches the other filters.",
                                        false)
                                .str("player", "Only entries involving this mortal.", false)
                                .choice("kind", "Only this kind of entry: event (deeds of mortals), speech (chat and "
                                        + "voice), prayer (words and gifts to you), directive (from the operator), act "
                                        + "(your powers), thought (your reasoning), name (your names).", false,
                                        List.of("event", "speech", "prayer", "directive", "act", "thought", "name"))
                                .integer("hours", "Only the last this many hours. Default: everything kept.", false, 1,
                                        24 * 365)
                                .integer("limit", "Most lines to return (default 20, max " + MAX_LINES + ").", false, 1,
                                        MAX_LINES),
                        MemoryTools::searchAnnals),
                GodTool.sync("rename_self",
                        "Take a new name for yourself, and optionally a new epithet. Coin something otherworldly "
                                + "and invented, with the feel of Azaeleus, Vhalmyr or Oruun: never an ordinary "
                                + "human name, a famous god or character, a mortal's name, or a word like 'God'. "
                                + "At most once a day. No mortal is told; announce it yourself if you wish.",
                        Schema.object()
                                .str("name", "Your new name: one word of 2 to 24 letters; an apostrophe or hyphen "
                                        + "between letters is allowed (like Vhal'myr).", true)
                                .str("epithet", "A title that follows your name, e.g. 'the Violet Watcher'. Leave "
                                        + "out to keep your current one.", false),
                        MemoryTools::renameSelf));
    }

    static ToolResult recall(Args a) throws ToolException {
        String asked = a.str("player");
        Memory memory = Memory.get();
        Memory.Subject s = memory.findSubject(asked);
        if (s == null) {
            List<String> known = memory.knownNames();
            String list = known.isEmpty() ? "none" : String.join(", ", known.subList(0, Math.min(20, known.size())))
                    + (known.size() > 20 ? ", …" : "");
            throw new ToolException("You remember no mortal called '" + asked + "'. Mortals on record: " + list
                    + ". search_annals can still find mentions of them.");
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(memory.renderDossier(s.name, null, true, now));
        if (!"world".equalsIgnoreCase(s.name)) {
            Annals.Found found = memory.annals().search(null, s.name, null, 0, 8);
            if (!found.entries().isEmpty()) {
                sb.append("  latest in your annals (newest first):\n");
                for (Annals.Entry e : found.entries()) {
                    sb.append("    - ").append(annalLine(e, now, 200)).append("\n");
                }
                if (found.more()) sb.append("    (more: search_annals with player=").append(s.name).append(")\n");
            }
        }
        return ToolResult.ok(bounded(sb.toString(), "(cut short; use search_annals for the rest)"));
    }

    static ToolResult searchAnnals(Args a) throws ToolException {
        int limit = a.integer("limit", 20, 1, MAX_LINES);
        int hours = a.integer("hours", 0, 0, 24 * 365);
        long since = hours <= 0 ? 0 : System.currentTimeMillis() - hours * 3_600_000L;
        String query = a.str("query", null);
        String player = a.str("player", null);
        String kind = a.str("kind", null);
        Annals.Found found = Memory.get().annals().search(query, player, kind, since, limit);
        if (found.entries().isEmpty()) {
            return ToolResult.ok("Nothing in your annals matches"
                    + (GodSettings.annalsEnabled() ? "." : " (the annals are switched off in config.yml)."));
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append(found.entries().size()).append(found.more() ? "+" : "").append(" match(es), newest first:\n");
        int shown = 0;
        for (Annals.Entry e : found.entries()) {
            String line = "- " + annalLine(e, now, 300) + "\n";
            if (sb.length() + line.length() > MAX_OUTPUT - 120) break;
            sb.append(line);
            shown++;
        }
        if (found.more() || shown < found.entries().size()) {
            sb.append("(more matches exist; narrow the query, player, kind or hours)\n");
        }
        return ToolResult.ok(sb.toString().trim());
    }

    static ToolResult renameSelf(Args a) throws ToolException {
        Persona p = Persona.current();
        if (p == null) throw new ToolException("You have no identity yet to rename.");
        String before = p.title();
        List<String> taken = new ArrayList<>(Memory.get().knownNames());
        if (Bukkit.getServer() != null) {
            for (Player online : Bukkit.getOnlinePlayers()) taken.add(online.getName());
        }
        String problem = Persona.rename(a.str("name"), a.str("epithet", null), taken, System.currentTimeMillis());
        if (problem != null) throw new ToolException(problem);
        String after = p.title();
        Memory.get().addDeed("world", "I took a new name: " + after + " (I was " + before + ")");
        Memory.get().annals().append("name", List.of(), "The god took a new name: " + after + " (was " + before + ")");
        Journal.add(Journal.Kind.INFO, "The god renamed itself: " + before + " → " + after);
        try {
            Realm.current().vessel().refreshNameplateLater();
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.debug("Could not refresh the vessel's nameplate", e);
        }
        return ToolResult.ok("It is done: you are now " + after + ". No mortal has been told; your vessel's "
                + "nameplate shows the new name. Announce it yourself if you wish.");
    }

    private static String annalLine(Annals.Entry e, long now, int chars) {
        String who = e.players().isEmpty() ? "" : " (" + String.join(", ", e.players()) + ")";
        String text = e.text().length() > chars ? e.text().substring(0, chars - 1) + "…" : e.text();
        return "[" + Memory.age(now - e.at()) + "] " + e.kind() + who + ": " + text;
    }

    private static String bounded(String text, String note) {
        if (text.length() <= MAX_OUTPUT) return text.trim();
        return text.substring(0, MAX_OUTPUT - note.length() - 2).trim() + "\n" + note;
    }

    /** Uses the online player's exact name when possible so ledger keys stay consistent. */
    private static String canonicalName(String name) {
        try {
            return Targets.player(name).getName();
        } catch (ToolException | RuntimeException e) {
            return name; // not online (or no server at all): keep the name as given
        }
    }
}
