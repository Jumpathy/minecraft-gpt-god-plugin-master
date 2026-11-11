package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;

/**
 * A god's own library of effects it invented: library.json in its folder, one per god. Each entry keeps
 * the scene, the god's note on what it is for, and how often and when it was used, so the god can
 * build up a visual language of its own and recall it across restarts. An entry is an effect (kind "fx":
 * a scene) or a routine (kind "routine": steps, see {@link FxRoutines}); an effect may be bound to powers
 * ("bound"), and then plays whenever the god uses one of them.
 */
public final class FxLibrary {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_NAME = 32;
    public static final int MAX_BINDINGS = 12;
    public static final String FX = "fx";
    public static final String ROUTINE = "routine";

    public static final class Entry {
        public String name;
        public String kind = "fx";
        public String note;
        public JsonArray layers;
        /** A routine's steps (kind "routine"); null for an effect. */
        public List<FxRoutines.Step> steps;
        /** The powers this effect is bound to, or null. */
        public List<String> bound;
        public int uses;
        public long lastUsed;
        public long created;

        public boolean isRoutine() {
            return steps != null && layers == null;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, FxLibrary> BY_REALM = new ConcurrentHashMap<>();

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    FxLibrary(Path file) {
        this.file = file;
        load();
    }

    public static FxLibrary of(Realm r) {
        return BY_REALM.computeIfAbsent(r.id, k -> new FxLibrary(r.file("library.json")));
    }

    /** A name the god may use: lowercase words joined by underscores. */
    public static String key(String name) {
        if (name == null) return "";
        String k = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return k.length() > MAX_NAME ? k.substring(0, MAX_NAME) : k;
    }

    private synchronized void load() {
        if (file == null || !Files.exists(file)) return;
        try {
            List<Entry> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Entry>>() {
            }.getType());
            if (list != null) {
                for (Entry e : list) {
                    if (e == null || e.name == null || (e.layers == null && e.steps == null)) continue;
                    e.kind = e.layers == null ? ROUTINE : FX;
                    entries.put(key(e.name), e);
                }
            }
        } catch (IOException | RuntimeException e) {
            // an unreadable library starts empty; the next save replaces it
        }
    }

    private synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(new ArrayList<>(entries.values())), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // kept in memory; tried again on the next change
        }
    }

    /** Keeps a scene under a name (replacing one of that name). When full, the least used old one goes. */
    public synchronized Entry keep(String name, String note, JsonArray layers, long now) {
        String k = key(name);
        if (k.isEmpty()) throw new IllegalArgumentException("Give the effect a name of letters and numbers.");
        Entry old = entries.get(k);
        Entry e = new Entry();
        e.name = k;
        e.note = note == null || note.isBlank() ? (old == null ? null : old.note) : clip(note.trim(), 160);
        e.layers = layers;
        e.bound = old == null || old.isRoutine() ? null : old.bound;
        e.uses = old == null ? 0 : old.uses;
        e.created = old == null ? now : old.created;
        e.lastUsed = now;
        entries.put(k, e);
        if (entries.size() > MAX_ENTRIES) {
            entries.values().stream().filter(x -> x != e).min(Comparator.<Entry>comparingInt(x -> x.uses)
                    .thenComparingLong(x -> x.lastUsed)).ifPresent(x -> entries.remove(x.name));
        }
        save();
        return e;
    }

    /** The effect of this name (not a routine), or null. */
    public synchronized Entry find(String name) {
        Entry e = entries.get(key(name));
        return e == null || e.isRoutine() ? null : e;
    }

    /** The entry of this name, effect or routine, or null. */
    public synchronized Entry any(String name) {
        return entries.get(key(name));
    }

    /** Keeps a routine under a name (replacing an entry of that name, and any binding it had). */
    public synchronized Entry keepRoutine(String name, String note, List<FxRoutines.Step> steps, long now) {
        String k = key(name);
        if (k.isEmpty()) throw new IllegalArgumentException("Give the routine a name of letters and numbers.");
        Entry old = entries.get(k);
        Entry e = new Entry();
        e.name = k;
        e.kind = ROUTINE;
        e.note = note == null || note.isBlank() ? (old == null ? null : old.note) : clip(note.trim(), 160);
        e.steps = new ArrayList<>(steps);
        e.uses = old == null ? 0 : old.uses;
        e.created = old == null ? now : old.created;
        e.lastUsed = now;
        entries.put(k, e);
        if (entries.size() > MAX_ENTRIES) {
            entries.values().stream().filter(x -> x != e).min(Comparator.<Entry>comparingInt(x -> x.uses)
                    .thenComparingLong(x -> x.lastUsed)).ifPresent(x -> entries.remove(x.name));
        }
        save();
        return e;
    }

    /** Changes an entry's note without touching the rest. */
    public synchronized void note(Entry e, String note) {
        e.note = note == null || note.isBlank() ? null : clip(note.trim(), 160);
        save();
    }

    // ---- Bound effects ---------------------------------------------------------------------------

    /** The effect bound to this power, or null. */
    public synchronized Entry boundTo(String power) {
        if (power == null) return null;
        for (Entry e : entries.values()) {
            if (e.bound != null && !e.isRoutine() && e.bound.contains(power)) return e;
        }
        return null;
    }

    /**
     * Binds a power to an effect (one effect per power; the power's old binding goes).
     *
     * @return the effect, or null when there is no effect of that name
     * @throws IllegalStateException when the god already has {@link #MAX_BINDINGS} bindings
     */
    public synchronized Entry bind(String power, String effect) {
        Entry e = find(effect);
        if (e == null) return null;
        boolean rebinding = boundTo(power) != null;
        if (!rebinding && bindings().size() >= MAX_BINDINGS) {
            throw new IllegalStateException("You already have " + MAX_BINDINGS + " bound effects; unbind one first.");
        }
        strip(power);
        if (e.bound == null) e.bound = new ArrayList<>();
        e.bound.add(power);
        save();
        return e;
    }

    /** Removes a power's binding. Returns the effect's name it had, or null. */
    public synchronized String unbind(String power) {
        String had = strip(power);
        if (had != null) save();
        return had;
    }

    private String strip(String power) {
        String had = null;
        for (Entry e : entries.values()) {
            if (e.bound != null && e.bound.remove(power)) {
                had = e.name;
                if (e.bound.isEmpty()) e.bound = null;
            }
        }
        return had;
    }

    /** power -> effect name, in library order. */
    public synchronized Map<String, String> bindings() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Entry e : entries.values()) {
            if (e.bound == null || e.isRoutine()) continue;
            for (String p : e.bound) out.put(p, e.name);
        }
        return out;
    }

    /** "smite → ashen_verdict; heal → dawn_ring", or "none". */
    public synchronized String bindingsText() {
        Map<String, String> b = bindings();
        if (b.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        b.forEach((p, n) -> sb.append(sb.length() > 0 ? "; " : "").append(p).append(" → ").append(n));
        return sb.toString();
    }

    /** Every entry as stored (a copy of the list), for the mind viewer. */
    public synchronized List<Entry> all() {
        return new ArrayList<>(entries.values());
    }

    public synchronized void used(Entry e, long now) {
        e.uses++;
        e.lastUsed = now;
        save();
    }

    public synchronized boolean forget(String name) {
        boolean had = entries.remove(key(name)) != null;
        if (had) save();
        return had;
    }

    public synchronized int size() {
        return entries.size();
    }

    /** "name (note)" for each entry, most used first; for the god's tool text. */
    public synchronized String index(int max) {
        return index(false, max);
    }

    /** The same for routines: "name (note)" each, most used first. */
    public synchronized String routines(int max) {
        return index(true, max);
    }

    private String index(boolean routines, int max) {
        List<Entry> list = new ArrayList<>();
        for (Entry x : entries.values()) if (x.isRoutine() == routines) list.add(x);
        if (list.isEmpty()) return "none yet";
        list.sort(Comparator.<Entry>comparingInt(x -> -x.uses).thenComparingLong(x -> -x.lastUsed));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() && i < max; i++) {
            Entry e = list.get(i);
            if (i > 0) sb.append("; ");
            sb.append(e.name);
            if (e.note != null) sb.append(" (").append(e.note).append(')');
        }
        if (list.size() > max) sb.append("; and ").append(list.size() - max).append(" more");
        return sb.toString();
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
