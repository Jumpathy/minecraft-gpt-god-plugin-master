package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;

/**
 * Everything the god remembers between thoughts: favor and notes about each mortal, quests it
 * gave, a digest of its recent acts, and an older "chronicle" condensed by a cheap model.
 * Saved to plugins/gptgodmc/god/memory.json.
 */
public final class Memory {
    public static final int FAVOR_MIN = -10;
    public static final int FAVOR_MAX = 10;
    private static final int NOTES_PER_SUBJECT = 8;
    private static final int RECENT_KEEP = 12;
    private static final int RECENT_FOLD = 6;
    /** The newest cycle records shown in full; older ones get one line each. */
    private static final int RECENT_DETAILED = 4;
    private static final int WORDS_KEPT = 6;
    private static final int SUMMARY_MAX = 700;
    /** A dossier in the perception is trimmed to about this many characters (~250 tokens). */
    static final int DOSSIER_BUDGET = 1000;
    private static final long DEED_MERGE_MS = 10 * 60_000L;
    public static final int MAX_COMMANDMENTS = 7;
    public static final int COMMANDMENT_LENGTH = 120;
    private static final int OFFERINGS_KEPT = 5;
    /** Reads the recent-records deque leniently: a null entry (hand-edited file) is skipped, not fatal. */
    private static final com.google.gson.JsonDeserializer<Deque<CycleRecord>> RECENT_READER = (json, type, ctx) -> {
        Deque<CycleRecord> out = new ArrayDeque<>();
        if (json != null && json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) {
                if (e != null && e.isJsonObject()) out.add(ctx.deserialize(e, CycleRecord.class));
            }
        }
        return out;
    };
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(new com.google.gson.reflect.TypeToken<Deque<CycleRecord>>() {
            }.getType(), RECENT_READER).create();

    public static final class Note {
        public long at;
        public String text;

        Note(long at, String text) {
            this.at = at;
            this.text = text;
        }
    }

    public static final class Subject {
        public String name;
        public int favor;
        public List<Note> notes = new ArrayList<>();
        /** A title the god bestowed ("the Faithful"), shown after the name; null when none. */
        public String title;
        /** Adventure color name for the title (gold, red, ...). */
        public String titleColor;
        /** halo | curse, or null; lasts until {@link #markUntil}. */
        public String mark;
        public long markUntil;
        /** How many offerings this mortal has ever made, and the last few. */
        public int offerings;
        public List<Offering> offered = new ArrayList<>();
        /** Notable dated things this mortal did, or you did to them, oldest first (bounded). */
        public List<Note> deeds = new ArrayList<>();
        /** Older deeds and history folded into one paragraph by the memory model; null when none. */
        public String summary;
        /** The last few words between them and you ("they: ..." / "you whispered: ..."), oldest first. */
        public List<Note> words = new ArrayList<>();
        /** When they last took part in something you perceived (0 = unknown). */
        public long lastSeen;
        /** When you gave them something (items, xp, a creature, healing, empowerment), newest last; a week kept. */
        public List<Long> gifts = new ArrayList<>();
        /** Oaths they swore before you that you judged kept or broken, ever. */
        public int oathsKept;
        public int oathsBroken;
        transient boolean foldingDeeds;
    }

    /** One gift a mortal gave the god. */
    public static final class Offering {
        public long at;
        public String what;
        /** "vessel" (at the vessel's feet) or "sky" (/offer). */
        public String where;

        Offering(long at, String what, String where) {
            this.at = at;
            this.what = what;
            this.where = where;
        }
    }

    public static final class Quest {
        public String id;
        public String title;
        public String details;
        public String assignee;
        public long createdAt;
        public long expiresAt;
        /** active | completed | failed | expired */
        public String status = "active";
        public String outcome;
        public long resolvedAt;

        public boolean isActive() {
            return "active".equals(status);
        }
    }

    /** Something the god built. */
    public static final class Work {
        public String id;
        public String name;
        public String blueprint;
        public String world;
        public int x, y, z;
        public int blocks;
        public String size;
        public long builtAt;
    }

    public static final class CycleRecord {
        public long at;
        public String trigger;
        public String perceived;
        public String thought;
        public List<String> acts = new ArrayList<>();
    }

    public String chronicle = "";
    public Map<String, Subject> subjects = new LinkedHashMap<>();
    public List<Quest> quests = new ArrayList<>();
    public int questCounter = 0;
    public Deque<CycleRecord> recent = new ArrayDeque<>();
    public List<Work> works = new ArrayList<>();
    public int workCounter = 0;
    /** The god's own purpose: ambitions and missions (see {@link Purpose}). */
    public List<Purpose.Goal> goals = new ArrayList<>();
    public int goalCounter = 0;
    /** The pastime it chose for itself and is doing now (free will), and the last few it did. */
    public Initiative.Activity activity;
    public List<Initiative.Activity> pastActivities = new ArrayList<>();
    /** Animations it wrote and kept (vessel_animate save), by name, in Choreo text form. */
    public Map<String, String> dances = new LinkedHashMap<>();
    /** The god's standing laws, in order (numbered from 1 when shown). */
    public List<String> commandments = new ArrayList<>();
    /** The god's lasting feelings (wrath, joy, sorrow, dread, glory), fading over time. */
    public Heart heart = new Heart();
    /** Its wounds from the Godslayer, and whether it lies slain (survives restarts and /god forget). */
    public Mortality mortality = new Mortality();
    /** How it regards each other god of its world, and the pacts they share (god/living/Bonds). */
    public net.bigyous.gptgodmc.god.living.Bonds bonds = new net.bigyous.gptgodmc.god.living.Bonds();
    /** The mark mortals left on the land lately (see {@link Ecology}). */
    public Ecology.Ledger land = new Ecology.Ledger();
    /** Oaths sworn before the god, open and lately closed (see {@link Oaths}). */
    public List<Oaths.Oath> oaths = new ArrayList<>();
    public int oathCounter = 0;
    /** Its private beliefs, intentions and questions (see {@link Mind}). */
    public Mind.State mind = new Mind.State();
    private transient boolean folding = false;
    /** Where this memory is saved; null means it lives in RAM only (unit tests). */
    private transient Path file;
    /** Set once this memory has been replaced (e.g. /god forget), so it can never overwrite its successor. */
    private transient volatile boolean stale;
    private transient Annals annals;

    /** The memory of the god in question (see {@link Realm#current()}). */
    public static Memory get() {
        return Realm.current().memory();
    }

    // ---- Persistence ---------------------------------------------------------------------

    /** Reads a god's memory from disk, or starts a blank one. */
    static Memory open(Path file) {
        Memory m = null;
        if (file != null && Files.exists(file)) {
            try {
                Memory loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Memory.class);
                if (loaded != null) m = loaded.normalized();
            } catch (IOException | RuntimeException e) {
                GPTGOD.LOGGER.warn("Could not read " + file + ", starting with a blank memory", e);
            }
        }
        if (m == null) m = new Memory();
        m.file = file;
        return m;
    }

    /**
     * A memory parsed from JSON (the mind viewer's raw editor) that takes the place of `old`: same file,
     * and `old` is never saved again. Throws when the text is not a memory.
     */
    static Memory replacing(Memory old, String json) {
        Memory m = GSON.fromJson(json, Memory.class);
        if (m == null) throw new IllegalArgumentException("empty memory");
        m = m.normalized();
        m.file = old.file;
        old.stale = true;
        return m;
    }

    /** Re-reads the current god's memory from disk. */
    public static void load() {
        Realm realm = Realm.current();
        realm.setMemory(open(realm.file("memory.json")));
    }

    private static final Object FILE_LOCK = new Object();

    /** Writes memory.json atomically (temp file + move), one writer at a time. */
    public void save() {
        if (file == null || stale) {
            return;
        }
        String json;
        synchronized (this) {
            json = GSON.toJson(this);
        }
        synchronized (FILE_LOCK) {
            try {
                Path target = file;
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling("memory.json.tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                GPTGOD.LOGGER.warn("Could not save memory.json", e);
            }
        }
    }

    /** Never saved again (its god was removed). */
    void retire() {
        stale = true;
    }

    /** Wipes the current god's memory (/god forget). */
    public static void reset() {
        Realm realm = Realm.current();
        Memory old = realm.memory();
        old.stale = true;
        Memory fresh = new Memory();
        fresh.file = old.file;
        fresh.mortality = old.mortality; // forgetting does not raise the dead
        realm.setMemory(fresh);
        fresh.save();
    }

    private Memory normalized() {
        if (chronicle == null) chronicle = "";
        if (heart == null) heart = new Heart();
        if (mortality == null) mortality = new Mortality();
        if (bonds == null) bonds = new net.bigyous.gptgodmc.god.living.Bonds();
        if (land == null) land = new Ecology.Ledger();
        if (land.cells == null) land.cells = new LinkedHashMap<>();
        land.cells.values().removeIf(c -> c == null || c.world == null || c.by == null);
        if (mind == null) mind = new Mind.State();
        if (mind.thoughts == null) mind.thoughts = new ArrayList<>();
        mind.thoughts.removeIf(t -> t == null || t.id == null || t.text == null || !Mind.KINDS.contains(t.kind));
        for (Mind.Thought t : mind.thoughts) {
            if (t.about == null) t.about = new ArrayList<>();
        }
        if (oaths == null) oaths = new ArrayList<>();
        oaths.removeIf(o -> o == null || o.id == null || o.swearer == null || o.status == null);
        for (Oaths.Oath o : oaths) {
            if (o.words == null) o.words = "";
            if (o.terms == null) o.terms = o.words;
            if (o.watch != null && (o.watch.act == null || o.watch.kind == null)) o.watch = null;
        }
        if (subjects == null) subjects = new LinkedHashMap<>();
        if (quests == null) quests = new ArrayList<>();
        if (recent == null) recent = new ArrayDeque<>();
        if (works == null) works = new ArrayList<>();
        if (goals == null) goals = new ArrayList<>();
        if (pastActivities == null) pastActivities = new ArrayList<>();
        pastActivities.removeIf(java.util.Objects::isNull);
        if (activity != null && (activity.kind == null || activity.notes == null)) {
            if (activity.kind == null) activity = null;
            else activity.notes = new ArrayList<>();
        }
        for (Initiative.Activity x : pastActivities) {
            if (x.notes == null) x.notes = new ArrayList<>();
        }
        if (dances == null) dances = new LinkedHashMap<>();
        if (commandments == null) commandments = new ArrayList<>();
        commandments.removeIf(c -> c == null || c.isBlank());
        subjects.values().removeIf(java.util.Objects::isNull);
        for (Subject s : subjects.values()) {
            if (s.notes == null) s.notes = new ArrayList<>();
            if (s.offered == null) s.offered = new ArrayList<>();
            if (s.deeds == null) s.deeds = new ArrayList<>();
            if (s.words == null) s.words = new ArrayList<>();
            s.notes.removeIf(n -> n == null || n.text == null);
            s.deeds.removeIf(n -> n == null || n.text == null);
            s.words.removeIf(n -> n == null || n.text == null);
            s.offered.removeIf(java.util.Objects::isNull);
        }
        quests.removeIf(java.util.Objects::isNull);
        recent.removeIf(java.util.Objects::isNull);
        for (CycleRecord r : recent) {
            if (r.acts == null) r.acts = new ArrayList<>();
        }
        return this;
    }

    /** This god's complete log (god/annals/), shared by every memory kept in the same folder. */
    public synchronized Annals annals() {
        if (annals == null) annals = Annals.forDir(file == null ? null : file.resolveSibling("annals"));
        return annals;
    }

    // ---- Works -----------------------------------------------------------------------------

    public synchronized Work addWork(String name, String blueprint, String world, int x, int y, int z, int blocks,
            String size) {
        Work w = new Work();
        w.id = "w" + (++workCounter);
        w.name = name == null || name.isBlank() ? blueprint : name;
        w.blueprint = blueprint;
        w.world = world;
        w.x = x;
        w.y = y;
        w.z = z;
        w.blocks = blocks;
        w.size = size;
        w.builtAt = System.currentTimeMillis();
        works.add(w);
        while (works.size() > 30) {
            works.remove(0);
        }
        return w;
    }

    /** The god's own name changed: what it remembers saying and thinking uses the new one. */
    public synchronized void renameSelf(String oldName, String newName) {
        if (oldName == null || oldName.isBlank() || newName == null) return;
        chronicle = Persona.swapName(chronicle, oldName, newName);
        for (CycleRecord r : recent) {
            r.thought = Persona.swapName(r.thought, oldName, newName);
            r.perceived = Persona.swapName(r.perceived, oldName, newName);
            r.acts.replaceAll(a -> Persona.swapName(a, oldName, newName));
        }
        save();
    }

    /** When this god last gave a quest to this mortal (or to "all"); 0 if never. */
    public synchronized long lastQuestFor(String assignee) {
        long last = 0;
        for (Quest q : quests) {
            if (q.assignee != null && (q.assignee.equalsIgnoreCase(assignee) || q.assignee.equalsIgnoreCase("all"))) {
                last = Math.max(last, q.createdAt);
            }
        }
        return last;
    }

    public synchronized Work findWork(String idOrName) {
        for (Work w : works) {
            if (w.id.equalsIgnoreCase(idOrName) || w.name.equalsIgnoreCase(idOrName)) {
                return w;
            }
        }
        return null;
    }

    public synchronized void removeWork(String id) {
        works.removeIf(w -> w.id.equals(id));
    }

    public synchronized String renderWorks() {
        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();
        for (Work w : works) {
            sb.append(String.format("- [%s] \"%s\" (%s, %s) at (%d, %d, %d) in %s, built %s%n", w.id, w.name,
                    w.blueprint, w.size, w.x, w.y, w.z, w.world, EventLogger.ago(Duration.ofMillis(now - w.builtAt))));
        }
        return sb.toString();
    }

    // ---- Favor and notes -----------------------------------------------------------------

    public synchronized Subject subject(String name) {
        return subjects.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> {
            Subject s = new Subject();
            s.name = name;
            return s;
        });
    }

    /** Names of every mortal you keep a record on (not 'world'). */
    public synchronized List<String> knownNames() {
        List<String> out = new ArrayList<>();
        for (Subject s : subjects.values()) {
            if (s.name != null && !"world".equalsIgnoreCase(s.name)) out.add(s.name);
        }
        return out;
    }

    /** A record by exact name, else the only one whose name starts with (then contains) the text. */
    public synchronized Subject findSubject(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim().toLowerCase(Locale.ROOT);
        Subject exact = subjects.get(t);
        if (exact != null) return exact;
        for (boolean prefix : new boolean[] { true, false }) {
            Subject found = null;
            int hits = 0;
            for (Subject s : subjects.values()) {
                String n = s.name == null ? "" : s.name.toLowerCase(Locale.ROOT);
                if (prefix ? n.startsWith(t) : n.contains(t)) {
                    found = s;
                    hits++;
                }
            }
            if (hits == 1) return found;
        }
        return null;
    }

    public synchronized int favorOf(String name) {
        Subject s = subjects.get(name.toLowerCase(Locale.ROOT));
        return s == null ? 0 : s.favor;
    }

    public synchronized int adjustFavor(String name, int delta, String reason) {
        Subject s = subject(name);
        s.favor = Math.max(FAVOR_MIN, Math.min(FAVOR_MAX, s.favor + delta));
        if (reason != null && !reason.isBlank()) {
            addNote(s, (delta >= 0 ? "+" : "") + delta + " favor: " + reason);
        }
        return s.favor;
    }

    public synchronized void remember(String about, String note) {
        addNote(subject(about), note);
    }

    // ---- Edits by the server operator (God Manager) ----------------------------------------

    /** Sets a mortal's favor outright; the note, if any, is kept as the god's own thought. */
    public synchronized int setFavor(String name, int favor, String note) {
        Subject s = subject(name);
        s.favor = Math.max(FAVOR_MIN, Math.min(FAVOR_MAX, favor));
        if (note != null && !note.isBlank()) addNote(s, note);
        return s.favor;
    }

    /** Removes one note (matched by its time and text). Returns whether one was removed. */
    public synchronized boolean removeNote(String name, long at, String text) {
        Subject s = subjectIfAny(name);
        return s != null && removeMatching(s.notes, at, text);
    }

    public synchronized boolean removeDeed(String name, long at, String text) {
        Subject s = subjectIfAny(name);
        return s != null && removeMatching(s.deeds, at, text);
    }

    private static boolean removeMatching(List<Note> list, long at, String text) {
        for (int i = list.size() - 1; i >= 0; i--) {
            Note n = list.get(i);
            if (n.at == at && (text == null || text.equals(n.text))) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    /** Forgets the god's notes about a mortal. Returns how many were removed. */
    public synchronized int clearNotes(String name) {
        Subject s = subjectIfAny(name);
        if (s == null) return 0;
        int n = s.notes.size();
        s.notes.clear();
        return n;
    }

    /** Forgets what a mortal did: their deeds and the summary of the past. Returns how many deeds went. */
    public synchronized int clearDeeds(String name) {
        Subject s = subjectIfAny(name);
        if (s == null) return 0;
        int n = s.deeds.size();
        s.deeds.clear();
        s.summary = null;
        return n;
    }

    /**
     * Forgets a mortal entirely: favor, title, notes, deeds, offerings and words. (The annals, the
     * permanent log, still hold what happened.) Returns whether there was a record.
     */
    public synchronized boolean forget(String name) {
        if (name == null || "world".equalsIgnoreCase(name)) return false;
        return subjects.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    private void addNote(Subject s, String text) {
        s.notes.add(new Note(System.currentTimeMillis(), text));
        while (s.notes.size() > NOTES_PER_SUBJECT) {
            s.notes.remove(0);
        }
    }

    public static String favorLabel(int favor) {
        if (favor <= -6) return "despised";
        if (favor <= -3) return "out of favor";
        if (favor < 3) return "unproven";
        if (favor < 6) return "favored";
        return "chosen";
    }

    /** The favor label with what it means for how you answer them. */
    public static String standing(int favor) {
        if (favor <= -6) return "despised: you owe them nothing, not even an answer";
        if (favor <= -3) return "out of favor: you may ignore their pleas";
        if (favor < 3) return "unproven";
        if (favor < 6) return "favored";
        return "chosen: you hold them dear";
    }

    // ---- Deeds and words ---------------------------------------------------------------------

    public void addDeed(String name, String text) {
        addDeed(name, text, System.currentTimeMillis());
    }

    /**
     * Remembers something notable a mortal did (or you did to them). A repeat of the last deed within
     * a few minutes only refreshes its time. Past the limit, the oldest are folded into their summary.
     */
    public synchronized void addDeed(String name, String text, long at) {
        if (name == null || name.isBlank() || text == null || text.isBlank()) return;
        Subject s = subject(name);
        String t = clip(oneLine(text), 160);
        Note last = s.deeds.isEmpty() ? null : s.deeds.get(s.deeds.size() - 1);
        if (last != null && last.text.equals(t) && Math.abs(at - last.at) < DEED_MERGE_MS) {
            last.at = Math.max(last.at, at);
            return;
        }
        s.deeds.add(new Note(at, t));
        trimDeeds(s);
    }

    private void trimDeeds(Subject s) {
        int keep = GodSettings.deedsKept();
        if (s.deeds.size() <= keep) return;
        if (s.foldingDeeds) {
            while (s.deeds.size() > keep * 2) s.deeds.remove(0);
            return;
        }
        List<Note> oldest = new ArrayList<>(s.deeds.subList(0, s.deeds.size() - keep / 2));
        s.foldingDeeds = true;
        Chronicler.foldDeedsAsync(s.name, s.summary, oldest, summary -> {
            synchronized (Memory.this) {
                if (summary != null) s.summary = clip(summary, SUMMARY_MAX);
                // Folded into the summary, or (when the memory model is unavailable) simply forgotten.
                s.deeds.removeAll(oldest);
                s.foldingDeeds = false;
            }
            save();
        });
    }

    /** Adds a sentence to a mortal's summary (from the chronicle fold); condenses it when it grows long. */
    synchronized void addToSummary(String name, String sentence) {
        Subject s = subjectIfAny(name);
        if (s == null || sentence == null || sentence.isBlank()) return;
        String line = clip(oneLine(sentence), 240);
        s.summary = s.summary == null || s.summary.isBlank() ? line : s.summary.trim() + " " + line;
        if (s.summary.length() > SUMMARY_MAX && !s.foldingDeeds) {
            s.foldingDeeds = true;
            Chronicler.foldDeedsAsync(s.name, s.summary, List.of(), summary -> {
                synchronized (Memory.this) {
                    s.summary = summary != null ? clip(summary, SUMMARY_MAX) : tailSentences(s.summary, SUMMARY_MAX);
                    s.foldingDeeds = false;
                }
                save();
            });
        }
    }

    /** One line of words exchanged with a mortal. */
    public synchronized void addWords(String name, String text, long at) {
        if (name == null || name.isBlank() || text == null || text.isBlank()) return;
        Subject s = subject(name);
        s.words.add(new Note(at, clip(oneLine(text), 160)));
        while (s.words.size() > WORDS_KEPT) s.words.remove(0);
    }

    /** Marks the mortals you can see right now as seen (only those you already keep a record on). */
    public synchronized void seen(Collection<String> names, long now) {
        for (String n : names) {
            Subject s = subjectIfAny(n);
            if (s != null) s.lastSeen = now;
        }
    }

    private static final Pattern PRAYS = Pattern.compile("^(\\S+) prays to you: \"(.*)\"$", Pattern.DOTALL);
    private static final Pattern SAID = Pattern.compile("^(\\S+) said (?:in chat|aloud \\(voice\\)): \"(.*)\"$",
            Pattern.DOTALL);
    private static final Pattern KILLED = Pattern.compile("^(.+) was killed by (\\S+)$");
    private static final Pattern ADVANCED = Pattern
            .compile("^(\\S+) has (?:made the advancement|completed the challenge|reached the goal) (.+)$");
    private static final Pattern TAMED = Pattern.compile("^(\\S+) tamed (an? .+?)\\.?$");
    private static final Pattern ENTERED = Pattern.compile("^(\\S+) entered the world$");

    /**
     * Called for every event the god's log receives: writes it to the annals and keeps the notable
     * ones as deeds (deaths, kills, prayers, advancements...) and words aimed at the god.
     *
     * @param online names of mortals online now, when known (they may not have a record yet)
     */
    public void noticeEvent(String category, String text, boolean addressed, Collection<String> online) {
        if (text == null || text.isBlank()) return;
        long now = System.currentTimeMillis();
        String kind = category == null ? "event" : category.toLowerCase(Locale.ROOT);
        List<String> names;
        synchronized (this) {
            names = namesIn(text, online);
            for (String n : names) {
                Subject s = subjectIfAny(n);
                if (s != null) s.lastSeen = now;
            }
            try {
                deedsFromEvent(kind, text, addressed, names, online, now);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.debug("Could not read a deed from an event", e);
            }
            try {
                moveHeart(kind, text, addressed, names, online, now);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.debug("Could not read a feeling from an event", e);
            }
        }
        annals().append(kind, names, text);
    }

    /** How the event stirs the god's lasting feelings (see {@link Heart#moved}). Lock held. */
    private void moveHeart(String kind, String text, boolean addressed, List<String> names, Collection<String> online,
            long now) {
        String who = null;
        String said = null;
        Matcher m;
        if (kind.equals("prayer") && (m = PRAYS.matcher(text)).matches()) {
            who = m.group(1);
            said = m.group(2);
        } else if (kind.equals("speech") && (m = SAID.matcher(text)).matches()) {
            who = m.group(1);
            said = m.group(2);
        } else if (kind.equals("event") && text.startsWith("DEATH: ")) {
            List<String> in = namesIn(text.substring(7).trim(), online);
            who = in.isEmpty() ? null : in.get(0);
        } else if (!names.isEmpty()) {
            who = names.get(0);
        }
        Persona p;
        try {
            p = Persona.current();
        } catch (RuntimeException e) {
            p = null; // no realm bound (tests)
        }
        heart.moved(kind, text, addressed, who, who == null ? 0 : favorOf(who), said, now,
                p == null ? 5 : p.dial("pride"), p == null ? 5 : p.dial("vanity"), p == null ? 5 : p.dial("wrath"));    }

    private void deedsFromEvent(String kind, String text, boolean addressed, List<String> names,
            Collection<String> online, long now) {
        Matcher m;
        switch (kind) {
            case "prayer" -> {
                if (text.contains(" OFFERING")) return; // kept by recordOffering
                if ((m = PRAYS.matcher(text)).matches()) {
                    String said = clip(m.group(2), 100);
                    addDeed(m.group(1), "prayed: \"" + said + "\"", now);
                    addWords(m.group(1), "they prayed: \"" + said + "\"", now);
                } else if (!names.isEmpty() && text.startsWith(names.get(0))) {
                    String rest = text.substring(names.get(0).length()).trim();
                    if (text.contains("touched your vessel") || text.contains("STRUCK your vessel")
                            || text.contains("WOUNDED you with the Godslayer")) {
                        addDeed(names.get(0), rest.replace("STRUCK", "struck").replaceAll("!$", ""), now);
                    } else {
                        addWords(names.get(0), "they: " + clip(rest, 120), now);
                    }
                }
            }
            case "speech" -> {
                if (addressed && (m = SAID.matcher(text)).matches()) {
                    addWords(m.group(1), "they: \"" + clip(m.group(2), 120) + "\"", now);
                }
            }
            case "event" -> {
                if (text.startsWith("DEATH: ")) {
                    String msg = text.substring(7).trim();
                    List<String> in = namesIn(msg, online);
                    if (in.isEmpty() || !msg.startsWith(in.get(0))) return;
                    String victim = in.get(0);
                    String how = msg.substring(victim.length()).trim();
                    addDeed(victim, "died: " + how, now);
                    for (int i = 1; i < in.size(); i++) {
                        addDeed(in.get(i), "killed " + victim + " (" + victim + " " + how + ")", now);
                    }
                } else if ((m = KILLED.matcher(text)).matches()) {
                    String victim = m.group(1), killer = m.group(2);
                    Set<String> people = candidates(online);
                    if (contains(people, killer) && !contains(people, victim)) {
                        addDeed(killer, "killed " + victim, now);
                    }
                } else if ((m = ADVANCED.matcher(text)).matches()) {
                    addDeed(m.group(1), "earned " + m.group(2), now);
                } else if ((m = TAMED.matcher(text)).matches()) {
                    addDeed(m.group(1), "tamed " + m.group(2), now);
                } else if ((m = ENTERED.matcher(text)).matches()) {
                    if (subjectIfAny(m.group(1)) == null) {
                        addDeed(m.group(1), "first entered your world", now);
                    }
                    subject(m.group(1)).lastSeen = now;
                }
            }
            default -> {
            }
        }
    }

    /** Powers that never become a deed (they are looks, notes, or recorded in their own way). */
    private static final Set<String> NO_DEED = Set.of("remember", "judge", "set_quest", "resolve_quest",
            "observe_player", "survey_area", "look", "recall", "search_annals", "rename_self", "vessel_gesture",
            "vessel_mood", "vessel_vanish", "blueprints", "decree", "whisper", "announce", "vessel_say", "map", "see_build");

    /** Reads one act ("name{json} → result") for deeds and words. */
    private void deedsFromAct(String act, long at) {
        int brace = act.indexOf('{');
        if (brace <= 0) return;
        String tool = act.substring(0, brace);
        int ok = act.indexOf("} → ");
        int bad = act.indexOf("} ✗ ");
        if (ok < 0 || (bad >= 0 && bad < ok)) return; // the power failed
        Map<String, String> args = parseArgs(act.substring(brace, ok + 1));
        String result = act.substring(ok + 4).trim();
        heart.acted(tool, args.get("mood"), at);
        String player = args.get("player");
        if (player != null && (player.equalsIgnoreCase("all") || player.equalsIgnoreCase("everyone"))) player = null;
        switch (tool) {
            case "whisper" -> {
                if (player != null && args.containsKey("message")) {
                    addWords(canonical(player), "you whispered: \"" + clip(args.get("message"), 120) + "\"", at);
                }
                return;
            }
            case "announce", "vessel_say" -> {
                String msg = args.get("message");
                if (msg == null) return;
                // Spoken to everyone: remembered for whoever addressed you in the last few minutes.
                for (Subject s : new ArrayList<>(subjects.values())) {
                    boolean addressed = false;
                    for (int i = s.words.size() - 1; i >= 0 && !addressed; i--) {
                        Note n = s.words.get(i);
                        addressed = n.text.startsWith("they") && at - n.at < 5 * 60_000L;
                    }
                    if (addressed) {
                        addWords(s.name, (tool.equals("announce") ? "you proclaimed: \"" : "you said aloud: \"")
                                + clip(msg, 120) + "\"", at);
                    }
                }
                return;
            }
            default -> {
            }
        }
        if (NO_DEED.contains(tool)) return;
        String who = player;
        if (who == null && args.get("target") != null && subjectIfAny(args.get("target")) != null) {
            who = args.get("target");
        }
        if (who == null && args.get("near") != null && subjectIfAny(args.get("near")) != null) {
            who = args.get("near");
        }
        if (who == null) return;
        addDeed(canonical(who), "you: " + tool + " — " + clip(result, 90), at);
        if (isGift(tool, args)) addGift(canonical(who), at);
    }

    /** Powers that hand a mortal something good. */
    private static final Set<String> GIFTS = Set.of("give_item", "supply_chest", "grant_xp", "heal", "revive",
            "empower");
    private static final Set<String> HOSTILE = Set.of("zombie", "skeleton", "creeper", "spider", "cave_spider",
            "witch", "enderman", "blaze", "ghast", "slime", "magma_cube", "phantom", "drowned", "husk", "stray",
            "pillager", "vindicator", "evoker", "ravager", "vex", "warden", "wither", "wither_skeleton", "piglin_brute",
            "hoglin", "zoglin", "guardian", "elder_guardian", "shulker", "silverfish", "endermite", "bogged",
            "breeze", "creaking", "zombie_villager", "zombified_piglin", "ender_dragon", "lightning_bolt", "tnt");

    static boolean isGift(String tool, Map<String, String> args) {
        if (GIFTS.contains(tool)) return true;
        if ("apply_effect".equals(tool)) {
            String e = args.getOrDefault("effect", "").toLowerCase(Locale.ROOT);
            return !(e.contains("poison") || e.contains("wither") || e.contains("slow") || e.contains("weak")
                    || e.contains("blind") || e.contains("nausea") || e.contains("hunger") || e.contains("darkness")
                    || e.contains("levitation") || e.contains("harm") || e.contains("fatigue")
                    || e.contains("unluck") || e.contains("bad_omen"));
        }
        if ("spawn_entity".equals(tool)) {
            String e = args.getOrDefault("entity", "").toLowerCase(Locale.ROOT).replace("minecraft:", "");
            return !e.isEmpty() && !HOSTILE.contains(e) && !args.containsKey("hunt");
        }
        return false;
    }

    public synchronized void addGift(String name, long at) {
        Subject s = subject(name);
        if (s.gifts == null) s.gifts = new ArrayList<>();
        // One thought that gives several things (a cat and some cod) counts once.
        if (!s.gifts.isEmpty() && at - s.gifts.get(s.gifts.size() - 1) < 60_000L) return;
        s.gifts.add(at);
        while (!s.gifts.isEmpty() && at - s.gifts.get(0) > 7 * 86_400_000L) s.gifts.remove(0);
    }

    /** "3 in the last hour (latest 2m ago), 5 today" or null when you gave them nothing lately. */
    static String giftsLine(Subject s, long now) {
        if (s.gifts == null || s.gifts.isEmpty()) return null;
        int hour = 0, day = 0, week = 0;
        long latest = 0;
        for (long t : s.gifts) {
            long ago = now - t;
            if (ago <= 3_600_000L) hour++;
            if (ago <= 86_400_000L) day++;
            if (ago <= 7 * 86_400_000L) week++;
            latest = Math.max(latest, t);
        }
        if (week == 0) return null;
        StringBuilder sb = new StringBuilder();
        if (hour > 0) sb.append(hour).append(" in the last hour, ");
        if (day > hour) sb.append(day).append(" today, ");
        if (week > day) sb.append(week).append(" this week, ");
        sb.append("latest ").append(age(now - latest));
        return sb.toString();
    }

    private String canonical(String name) {
        Subject s = subjectIfAny(name);
        return s != null && s.name != null ? s.name : name;
    }

    private static final Pattern ARG = Pattern.compile("\"([a-z_]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** The string arguments of an act (tolerates JSON that was cut short). */
    static Map<String, String> parseArgs(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsString());
            }
            return out;
        } catch (RuntimeException e) {
            Matcher m = ARG.matcher(json);
            while (m.find()) out.put(m.group(1), m.group(2).replace("\\\"", "\""));
            return out;
        }
    }

    private Set<String> candidates(Collection<String> online) {
        Set<String> out = new LinkedHashSet<>();
        if (online != null) out.addAll(online);
        for (Subject s : subjects.values()) {
            if (s.name != null && !"world".equalsIgnoreCase(s.name)) out.add(s.name);
        }
        return out;
    }

    private static boolean contains(Set<String> names, String name) {
        for (String n : names) {
            if (n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Mortals (online now or on record) named in the text, in order of first mention. */
    public synchronized List<String> namesIn(String text, Collection<String> online) {
        if (text == null || text.isEmpty()) return List.of();
        List<int[]> found = new ArrayList<>();
        List<String> names = new ArrayList<>(candidates(online));
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i).toLowerCase(Locale.ROOT);
            if (n.length() < 2) continue;
            int from = 0;
            while (true) {
                int at = lower.indexOf(n, from);
                if (at < 0) break;
                int end = at + n.length();
                boolean left = at == 0 || !isNameChar(lower.charAt(at - 1));
                boolean right = end >= lower.length() || !isNameChar(lower.charAt(end));
                if (left && right) {
                    found.add(new int[] { at, i });
                    break;
                }
                from = at + 1;
            }
        }
        found.sort(Comparator.comparingInt(a -> a[0]));
        List<String> out = new ArrayList<>();
        for (int[] f : found) {
            String n = names.get(f[1]);
            if (out.stream().noneMatch(o -> o.equalsIgnoreCase(n))) out.add(n);
        }
        return out;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // ---- Quests ----------------------------------------------------------------------------

    public synchronized Quest addQuest(String title, String details, String assignee, int minutes) {
        Quest q = new Quest();
        q.id = "q" + (++questCounter);
        q.title = title;
        q.details = details;
        q.assignee = assignee;
        q.createdAt = System.currentTimeMillis();
        q.expiresAt = q.createdAt + Duration.ofMinutes(minutes).toMillis();
        quests.add(q);
        trimQuests();
        if (assignee != null && !"all".equalsIgnoreCase(assignee)) {
            addDeed(assignee, "was given your quest " + q.id + " \"" + title + "\"", q.createdAt);
        }
        return q;
    }

    /** Remembers how a quest ended on its mortal's record (call after setting its status). */
    public synchronized void questEnded(Quest q) {
        if (q == null || q.assignee == null || "all".equalsIgnoreCase(q.assignee)) return;
        String how = switch (q.status) {
            case "completed" -> "completed your quest";
            case "failed" -> "failed your quest";
            case "expired" -> "let your quest expire:";
            default -> q.status + ":";
        };
        addDeed(q.assignee, how + " \"" + q.title + "\"" + (q.outcome == null || q.outcome.isBlank() ? ""
                : " — " + clip(q.outcome, 80)), q.resolvedAt == 0 ? System.currentTimeMillis() : q.resolvedAt);
    }

    public synchronized Quest findQuest(String idOrTitle) {
        for (Quest q : quests) {
            if (q.id.equalsIgnoreCase(idOrTitle) || q.title.equalsIgnoreCase(idOrTitle)) {
                return q;
            }
        }
        return null;
    }

    public synchronized List<Quest> activeQuests() {
        return quests.stream().filter(Quest::isActive).toList();
    }

    /** Marks overdue quests as expired and returns them. */
    public synchronized List<Quest> expireQuests() {
        long now = System.currentTimeMillis();
        List<Quest> expired = new ArrayList<>();
        for (Quest q : quests) {
            if (q.isActive() && q.expiresAt < now) {
                q.status = "expired";
                q.resolvedAt = now;
                expired.add(q);
            }
        }
        for (Quest q : expired) {
            questEnded(q);
        }
        return expired;
    }

    private void trimQuests() {
        List<Quest> resolved = quests.stream().filter(q -> !q.isActive())
                .sorted(Comparator.comparingLong(q -> q.resolvedAt)).toList();
        int excess = resolved.size() - 6;
        for (int i = 0; i < excess; i++) {
            quests.remove(resolved.get(i));
        }
    }

    // ---- Recent acts and the chronicle ---------------------------------------------------

    public synchronized void record(CycleRecord record) {
        recent.addLast(record);
        noticeRecord(record);
        if (recent.size() > RECENT_KEEP && !folding) {
            List<CycleRecord> oldest = new ArrayList<>();
            for (CycleRecord r : recent) {
                if (oldest.size() >= RECENT_FOLD) break;
                oldest.add(r);
            }
            folding = true;
            Chronicler.foldAsync(chronicle, oldest, folded -> {
                synchronized (Memory.this) {
                    if (folded != null) {
                        chronicle = folded.chronicle();
                        recent.removeAll(oldest);
                        folded.players().forEach(this::addToSummary);
                    } else {
                        // Could not condense: keep the newest records rather than growing forever.
                        while (recent.size() > RECENT_KEEP + RECENT_FOLD) {
                            recent.removeFirst();
                        }
                    }
                    folding = false;
                }
                save();
            });
        }
    }

    /** One act performed outside a normal thought (console, bridge, external god mode). */
    public synchronized void recordOutsideAct(String source, String act) {
        CycleRecord last = recent.peekLast();
        if (last != null && ("outside:" + source).equals(last.trigger)
                && System.currentTimeMillis() - last.at < 60_000) {
            last.acts.add(act);
            noticeAct(act, System.currentTimeMillis());
            return;
        }
        CycleRecord r = new CycleRecord();
        r.at = System.currentTimeMillis();
        r.trigger = "outside:" + source;
        r.acts.add(act);
        record(r);
    }

    /** Writes a finished thought and its acts to the annals and to the deeds of those it touched. */
    private void noticeRecord(CycleRecord r) {
        long at = r.at == 0 ? System.currentTimeMillis() : r.at;
        if (r.thought != null && !r.thought.isBlank()) {
            annals().append("thought", namesIn(r.thought, List.of()), r.thought);
        }
        for (String act : r.acts) {
            noticeAct(act, at);
        }
    }

    private void noticeAct(String act, long at) {
        if (act == null) return;
        try {
            deedsFromAct(act, at);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.debug("Could not read a deed from an act", e);
        }
        annals().append("act", namesIn(act, List.of()), act);
    }

    // ---- Devotion: offerings, titles, marks, commandments ------------------------------------

    /** The mortal's record if the god has one, without creating it. */
    public synchronized Subject subjectIfAny(String name) {
        return name == null ? null : subjects.get(name.toLowerCase(Locale.ROOT));
    }

    /** Remembers a gift; returns how many offerings this mortal has made in all. */
    public synchronized int recordOffering(String name, String what, String where) {
        Subject s = subject(name);
        s.offerings++;
        s.offered.add(new Offering(System.currentTimeMillis(), what, where));
        while (s.offered.size() > OFFERINGS_KEPT) {
            s.offered.remove(0);
        }
        addDeed(name, "offered " + what + ("vessel".equals(where) ? " at your vessel" : ""));
        return s.offerings;
    }

    public synchronized String titleOf(String name) {
        Subject s = subjectIfAny(name);
        return s == null ? null : s.title;
    }

    /** Sets (or with a blank title, removes) a mortal's title. Returns the previous one, or null. */
    public synchronized String setTitle(String name, String title, String color) {
        Subject s = title == null || title.isBlank() ? subjectIfAny(name) : subject(name);
        if (s == null) return null;
        String before = s.title;
        if (title == null || title.isBlank()) {
            s.title = null;
            s.titleColor = null;
        } else {
            s.title = title;
            s.titleColor = color;
        }
        return before;
    }

    /** The mark on a mortal if it has not run out yet ("halo" or "curse"), else null. */
    public synchronized String activeMark(String name, long now) {
        Subject s = subjectIfAny(name);
        if (s == null || s.mark == null) return null;
        return s.markUntil > now ? s.mark : null;
    }

    public synchronized void setMark(String name, String kind, long until) {
        Subject s = kind == null ? subjectIfAny(name) : subject(name);
        if (s == null) return;
        s.mark = kind;
        s.markUntil = kind == null ? 0 : until;
    }

    /** Clears a mark whose time is up; true when there was one to clear. */
    public synchronized boolean expireMark(String name, long now) {
        Subject s = subjectIfAny(name);
        if (s == null || s.mark == null || s.markUntil > now) return false;
        s.mark = null;
        s.markUntil = 0;
        return true;
    }

    /** Makes a law fit for stone: one line, no formatting codes, not too long. */
    public static String cleanCommandment(String text) {
        if (text == null) return "";
        String t = text.replaceAll("[\\u00a7&][0-9a-fk-orA-FK-ORxX]", "").replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        t = t.replaceFirst("^(?:[0-9]+|[IVXivx]+)[.):]\\s+", "");
        return t;
    }

    private static String checkedCommandment(String text) {
        String t = cleanCommandment(text);
        if (t.isEmpty()) throw new IllegalArgumentException("A commandment needs words.");
        if (t.length() > COMMANDMENT_LENGTH) {
            throw new IllegalArgumentException("That commandment is " + t.length() + " characters; carve it in at most "
                    + COMMANDMENT_LENGTH + ".");
        }
        return t;
    }

    public synchronized List<String> commandments() {
        return List.copyOf(commandments);
    }

    /** Adds a law at the end; returns its number. */
    public synchronized int addCommandment(String text) {
        String t = checkedCommandment(text);
        if (commandments.size() >= MAX_COMMANDMENTS) {
            throw new IllegalArgumentException("You already have " + MAX_COMMANDMENTS
                    + " commandments; revoke one or replace them all first.");
        }
        commandments.add(t);
        return commandments.size();
    }

    /** Removes law number n (from 1) and returns its text. */
    public synchronized String revokeCommandment(int number) {
        if (number < 1 || number > commandments.size()) {
            throw new IllegalArgumentException(commandments.isEmpty() ? "You have decreed no commandments."
                    : "There is no commandment " + number + "; you have " + commandments.size() + ".");
        }
        return commandments.remove(number - 1);
    }

    /** Replaces every law at once (an empty list abolishes them all). */
    public synchronized void replaceCommandments(List<String> laws) {
        List<String> fresh = new ArrayList<>();
        for (String law : laws) {
            if (cleanCommandment(law).isEmpty()) continue;
            fresh.add(checkedCommandment(law));
        }
        if (fresh.size() > MAX_COMMANDMENTS) {
            throw new IllegalArgumentException("At most " + MAX_COMMANDMENTS + " commandments; you gave "
                    + fresh.size() + ".");
        }
        commandments.clear();
        commandments.addAll(fresh);
    }

    /** "1. …" lines, or an empty string when there are none. */
    public synchronized String renderCommandments() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commandments.size(); i++) {
            sb.append(i + 1).append(". ").append(commandments.get(i)).append("\n");
        }
        return sb.toString();
    }

    // ---- Rendering for the god's perception ------------------------------------------------

    public synchronized String renderChronicle() {
        return chronicle == null || chronicle.isBlank() ? "" : chronicle.trim();
    }

    /**
     * The ledger for operators and tools (/god, the debug bridge): a dossier for everyone online and for
     * the ten mortals on record seen most recently. Perceptions use the focused two-argument form.
     */
    public synchronized String renderLedger(Collection<String> onlineNames) {
        List<String> everyone = new ArrayList<>(onlineNames);
        List<Subject> offline = new ArrayList<>();
        for (Subject s : subjects.values()) {
            if (s.name == null || "world".equalsIgnoreCase(s.name) || containsIgnoreCase(everyone, s.name)) continue;
            if (s.notes.isEmpty() && s.deeds.isEmpty() && s.offerings == 0) continue;
            offline.add(s);
        }
        offline.sort(Comparator.comparingLong((Subject s) -> -s.lastSeen));
        for (int i = 0; i < offline.size() && i < 10; i++) everyone.add(offline.get(i).name);
        return renderLedger(onlineNames, everyone, Integer.MAX_VALUE);
    }

    /**
     * The ledger as the perception shows it: a full dossier for each mortal involved in this moment
     * (up to memory.max-dossiers), one line for every other mortal online, and your notes on the world.
     * Offline mortals appear only when involved.
     */
    public String renderLedger(Collection<String> onlineNames, Collection<String> involved) {
        return renderLedger(onlineNames, involved, GodSettings.maxDossiers());
    }

    private synchronized String renderLedger(Collection<String> onlineNames, Collection<String> involved,
            int maxDossiers) {
        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();
        List<String> dossiers = new ArrayList<>();
        for (String n : involved) {
            if ("world".equalsIgnoreCase(n) || dossiers.size() >= maxDossiers) continue;
            if (dossiers.stream().noneMatch(d -> d.equalsIgnoreCase(n))) dossiers.add(n);
        }
        for (String n : dossiers) {
            sb.append(renderDossier(n, containsIgnoreCase(onlineNames, n), false, now));
        }
        for (String n : onlineNames) {
            if (containsIgnoreCase(dossiers, n)) continue;
            sb.append(renderLine(n, now));
        }
        Subject world = subjectIfAny("world");
        if (world != null && !world.notes.isEmpty()) {
            sb.append("- world: ");
            List<String> parts = new ArrayList<>();
            for (int i = Math.max(0, world.notes.size() - 3); i < world.notes.size(); i++) {
                Note n = world.notes.get(i);
                parts.add(clip(n.text, 100) + " (" + age(now - n.at) + ")");
            }
            sb.append(String.join("; ", parts)).append("\n");
        }
        return sb.toString();
    }

    /** One compact line for a mortal who is present but not part of this moment. */
    private String renderLine(String name, long now) {
        Subject s = subjectIfAny(name);
        if (s == null || (s.notes.isEmpty() && s.deeds.isEmpty() && s.favor == 0 && s.title == null)) {
            return "- " + name + ": new to you\n";
        }
        StringBuilder sb = new StringBuilder("- ").append(s.name).append(" (").append(signed(s.favor)).append(", ")
                .append(favorLabel(s.favor)).append(")");
        if (s.title != null) sb.append(" titled \"").append(s.title).append("\"");
        Note latestDeed = s.deeds.isEmpty() ? null : s.deeds.get(s.deeds.size() - 1);
        Note latestNote = s.notes.isEmpty() ? null : s.notes.get(s.notes.size() - 1);
        Note latest = latestDeed == null ? latestNote
                : latestNote == null || latestDeed.at >= latestNote.at ? latestDeed : latestNote;
        sb.append(": ").append(s.deeds.size()).append(" deeds, ").append(s.notes.size()).append(" notes");
        String gifts = giftsLine(s, now);
        if (gifts != null) sb.append(", your gifts: ").append(gifts);
        if (latest != null) {
            sb.append("; latest: ").append(clip(latest.text, 70)).append(" (").append(age(now - latest.at)).append(")");
        }
        return sb.append("\n").toString();
    }

    /**
     * Everything you keep on one mortal. In the perception ({@code full} false) it is trimmed to about
     * {@link #DOSSIER_BUDGET} characters; for recall ({@code full} true) it shows all deeds, notes and words.
     *
     * @param online true/false when known, null when unknown (then "last seen" is shown)
     */
    public synchronized String renderDossier(String name, Boolean online, boolean full, long now) {
        Subject s = subjectIfAny(name);
        if (s == null) {
            return "### " + name + " — new to you: no favor, deeds or notes yet"
                    + (Boolean.FALSE.equals(online) ? " (not here)" : "") + "\n" + questsFor(name, full, now);
        }
        if (full) return dossier(s, online, now, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 1200, 200, true);
        int deeds = GodSettings.dossierDeeds();
        int notes = 3;
        int words = 2;
        String out = dossier(s, online, now, deeds, notes, words, 300, 80, false);
        while (out.length() > DOSSIER_BUDGET && (deeds > 1 || notes > 1 || words > 1)) {
            if (deeds > 2 || (deeds > 1 && notes <= 1 && words <= 1)) deeds--;
            else if (notes > 1) notes--;
            else words--;
            out = dossier(s, online, now, deeds, notes, words, 300, 80, false);
        }
        if (out.length() > DOSSIER_BUDGET) {
            out = dossier(s, online, now, 1, 1, 1, 160, 60, false);
        }
        return out;
    }

    private String dossier(Subject s, Boolean online, long now, int maxDeeds, int maxNotes, int maxWords,
            int summaryChars, int itemChars, boolean full) {
        StringBuilder sb = new StringBuilder("### ").append(s.name);
        boolean world = "world".equalsIgnoreCase(s.name);
        if (!world) {
            sb.append(" — favor ").append(signed(s.favor)).append(" (").append(standing(s.favor)).append(")");
            if (s.title != null) sb.append(", titled \"").append(s.title).append("\"");
            if (s.mark != null && s.markUntil > now) {
                sb.append(", bears your ").append(Marks.HALO.equals(s.mark) ? "halo" : "curse");
            }
            if (Boolean.FALSE.equals(online)) sb.append(", not here");
            if (!Boolean.TRUE.equals(online) && s.lastSeen > 0) sb.append(", last seen ").append(age(now - s.lastSeen));
        }
        sb.append("\n");
        if (s.summary != null && !s.summary.isBlank()) {
            sb.append("  past: ").append(clip(s.summary, summaryChars)).append("\n");
        }
        appendNotes(sb, "  notes: ", s.notes, maxNotes, itemChars, now, full);
        String gifts = giftsLine(s, now);
        if (gifts != null) sb.append("  your gifts to them: ").append(gifts).append("\n");
        appendNotes(sb, "  deeds (newest first): ", s.deeds, maxDeeds, itemChars, now, full);
        sb.append(questsFor(s.name, full, now));
        String oathLine = Oaths.dossierLine(this, s);
        if (oathLine != null) sb.append("  oaths: ").append(oathLine).append("\n");
        if (s.offerings > 0 && !s.offered.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (int i = s.offered.size() - 1; i >= 0 && parts.size() < (full ? OFFERINGS_KEPT : 2); i--) {
                Offering o = s.offered.get(i);
                parts.add(clip(o.what, 50) + ("vessel".equals(o.where) ? " at your vessel" : "") + " ("
                        + age(now - o.at) + ")");
            }
            sb.append("  offerings: ").append(s.offerings).append(" in all; latest: ").append(String.join("; ", parts))
                    .append("\n");
        }
        appendNotes(sb, "  words (newest first): ", s.words, maxWords, itemChars + 20, now, full);
        return sb.toString();
    }

    /** Newest first, one line (or one item per line when full). */
    private static void appendNotes(StringBuilder sb, String label, List<Note> list, int max, int chars, long now,
            boolean full) {
        if (list.isEmpty() || max <= 0) return;
        List<String> parts = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0 && parts.size() < max; i--) {
            Note n = list.get(i);
            parts.add(clip(n.text, chars) + " (" + age(now - n.at) + ")");
        }
        if (full) {
            sb.append(label.stripTrailing()).append("\n");
            parts.forEach(p -> sb.append("    - ").append(p).append("\n"));
        } else {
            sb.append(label).append(String.join("; ", parts));
            if (list.size() > parts.size()) sb.append("; +").append(list.size() - parts.size()).append(" older");
            sb.append("\n");
        }
    }

    private String questsFor(String name, boolean full, long now) {
        List<String> parts = new ArrayList<>();
        for (Quest q : quests) {
            if (q.assignee == null || !q.assignee.equalsIgnoreCase(name)) continue;
            if (q.isActive()) {
                parts.add("[" + q.id + "] ACTIVE \"" + q.title + "\" (" + Math.max(0, (q.expiresAt - now) / 60_000)
                        + " min left)");
            } else if (full || now - q.resolvedAt < Duration.ofHours(2).toMillis()) {
                parts.add("[" + q.id + "] " + q.status + " \"" + q.title + "\" (" + age(now - q.resolvedAt) + ")");
            }
        }
        return parts.isEmpty() ? "" : "  quests: " + String.join("; ", parts) + "\n";
    }

    private static boolean containsIgnoreCase(Collection<String> names, String name) {
        for (String n : names) {
            if (n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** "just now", "12m ago", "5h ago", "3d ago". */
    public static String age(long ms) {
        long m = Math.max(0, ms) / 60_000;
        if (m < 1) return "just now";
        if (m < 60) return m + "m ago";
        if (m < 48 * 60) return (m / 60) + "h ago";
        return (m / (24 * 60)) + "d ago";
    }

    static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)).stripTrailing() + "…";
    }

    static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    /** The end of a text, cut at a sentence start, at most max characters. */
    static String tailSentences(String s, int max) {
        if (s == null || s.length() <= max) return s;
        String tail = s.substring(s.length() - max);
        int dot = tail.indexOf(". ");
        return dot >= 0 && dot < tail.length() - 2 ? tail.substring(dot + 2) : tail;
    }

    public synchronized String renderQuests() {
        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();
        for (Quest q : quests) {
            if (q.isActive()) {
                long left = Math.max(0, (q.expiresAt - now) / 60_000);
                sb.append(String.format("- [%s] ACTIVE for %s: \"%s\"%s (%d min left)%n", q.id, q.assignee, q.title,
                        q.details == null || q.details.isBlank() ? "" : " — " + q.details, left));
            }
        }
        for (Quest q : quests) {
            if (!q.isActive() && now - q.resolvedAt < Duration.ofMinutes(20).toMillis()) {
                sb.append(String.format("- [%s] %s for %s: \"%s\"%s (%s)%n", q.id, q.status.toUpperCase(Locale.ROOT),
                        q.assignee, q.title, q.outcome == null ? "" : " — " + q.outcome,
                        EventLogger.ago(Duration.ofMillis(now - q.resolvedAt))));
            }
        }
        return sb.toString();
    }

    /**
     * Your recent thoughts, oldest first: the newest few in full (acts shortened), older ones as one
     * line each naming the powers used, and runs of idle looks folded into a single line.
     */
    public synchronized String renderRecent() {
        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();
        List<CycleRecord> list = new ArrayList<>(recent);
        int detailFrom = Math.max(0, list.size() - RECENT_DETAILED);
        int idle = 0;
        long idleSince = 0;
        for (int i = 0; i < list.size(); i++) {
            CycleRecord r = list.get(i);
            boolean detailed = i >= detailFrom;
            if (!detailed && r.acts.isEmpty()) {
                if (idle++ == 0) idleSince = r.at;
                continue;
            }
            if (idle > 0) {
                sb.append("- ").append(age(now - idleSince)).append(": ").append(idle)
                        .append(idle == 1 ? " look" : " looks").append(" where you did nothing\n");
                idle = 0;
            }
            sb.append("- ").append(age(now - r.at));
            if (r.trigger != null && r.trigger.startsWith("outside:")) {
                sb.append(" (acting outside your own thoughts, via ").append(r.trigger.substring(8)).append(")");
            } else if (r.trigger != null) {
                sb.append(" (").append(clip(r.trigger, 80)).append(")");
            }
            sb.append(":");
            if (!detailed) {
                List<String> powers = new ArrayList<>();
                for (String act : r.acts) powers.add(actHeadline(act));
                sb.append(" ").append(String.join(", ", powers)).append("\n");
                continue;
            }
            if (r.perceived != null && !r.perceived.isBlank()) {
                sb.append(" you noticed ").append(clip(r.perceived, 200)).append(".");
            }
            if (r.acts.isEmpty()) {
                sb.append(" You did nothing.\n");
            } else {
                sb.append("\n");
                for (String act : r.acts) {
                    sb.append("    · ").append(clip(act, 180)).append("\n");
                }
            }
        }
        if (idle > 0) {
            sb.append("- ").append(age(now - idleSince)).append(": ").append(idle)
                    .append(idle == 1 ? " look" : " looks").append(" where you did nothing\n");
        }
        return sb.toString();
    }

    /** "whisper→Steve" from "whisper{"player":"Steve",...} → ...". */
    static String actHeadline(String act) {
        int brace = act.indexOf('{');
        if (brace <= 0) return clip(act, 40);
        String tool = act.substring(0, brace);
        int end = act.indexOf("} → ");
        int bad = act.indexOf("} ✗ ");
        boolean failed = bad >= 0 && (end < 0 || bad < end);
        Map<String, String> args = parseArgs(act.substring(brace, failed ? bad + 1 : end < 0 ? act.length() : end + 1));
        String who = args.getOrDefault("player", args.get("target"));
        return tool + (who == null ? "" : "→" + clip(who, 24)) + (failed ? " (failed)" : "");
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }
}
