package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import net.bigyous.gptgodmc.god.script.ScriptLimits;

/**
 * A god's saved scripts: scripts.json in its folder, beside library.json (its effects and routines, see
 * {@link FxLibrary}; names are made the same way, so a routine step can name one). Each entry keeps the
 * program, the arguments it was saved with (its parameters and their defaults), the god's note and how often
 * it was used, so a script that worked can be run again by name with new arguments for a few tokens.
 */
public final class ScriptLibrary {
    public static final int MAX_ENTRIES = 32;

    public static final class Entry {
        public String name;
        public String note;
        public String code;
        /** The parameters: the arguments it was saved with. Arguments given at a later run replace them one by one. */
        public JsonObject defaults;
        public int uses;
        public long lastUsed;
        public long created;

        /** "height=8, block=\"gold_block\"", or "" when it takes none. */
        public String params() {
            if (defaults == null || defaults.size() == 0) return "";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                String v = e.getValue().toString();
                sb.append(e.getKey()).append('=').append(v.length() > 24 ? v.substring(0, 23) + "…" : v);
            }
            return sb.toString();
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, ScriptLibrary> BY_REALM = new ConcurrentHashMap<>();

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** @param file where it is kept, or null to keep it in memory only (tests, a removed god) */
    public ScriptLibrary(Path file) {
        this.file = file;
        load();
    }

    public static ScriptLibrary of(Realm r) {
        return BY_REALM.computeIfAbsent(r.id, k -> new ScriptLibrary(r.file("scripts.json")));
    }

    private synchronized void load() {
        if (file == null || !Files.exists(file)) return;
        try {
            List<Entry> list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Entry>>() {
            }.getType());
            if (list == null) return;
            for (Entry e : list) {
                if (e == null || e.name == null || e.code == null || e.code.isBlank()) continue;
                if (e.code.length() > ScriptLimits.MAX_CODE) continue;
                entries.put(FxLibrary.key(e.name), e);
            }
        } catch (IOException | RuntimeException e) {
            // an unreadable file starts empty; the next save replaces it
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

    /** Keeps a script under a name (replacing one of that name). When full, the least used old one goes. */
    public synchronized Entry keep(String name, String note, String code, JsonObject defaults, long now) {
        String k = FxLibrary.key(name);
        if (k.isEmpty()) throw new IllegalArgumentException("Give the script a name of letters and numbers.");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("There is no program to keep.");
        if (code.length() > ScriptLimits.MAX_CODE) throw new IllegalArgumentException("The script is too long to keep.");
        Entry old = entries.get(k);
        Entry e = new Entry();
        e.name = k;
        e.note = note == null || note.isBlank() ? (old == null ? null : old.note)
                : note.trim().length() > 160 ? note.trim().substring(0, 159) + "…" : note.trim();
        e.code = code;
        e.defaults = defaults == null ? new JsonObject() : defaults.deepCopy();
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

    public synchronized Entry find(String name) {
        return entries.get(FxLibrary.key(name));
    }

    /** The arguments a run gets: the saved ones, each replaced by the one given now. */
    public static JsonObject merged(Entry e, JsonObject given) {
        JsonObject out = e.defaults == null ? new JsonObject() : e.defaults.deepCopy();
        if (given != null) given.entrySet().forEach(x -> out.add(x.getKey(), x.getValue()));
        return out;
    }

    public synchronized void used(Entry e, long now) {
        e.uses++;
        e.lastUsed = now;
        save();
    }

    public synchronized boolean forget(String name) {
        boolean had = entries.remove(FxLibrary.key(name)) != null;
        if (had) save();
        return had;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized List<Entry> all() {
        return new ArrayList<>(entries.values());
    }

    /** "spiral(height=8, block="gold_block"): a tower of light; rain_of_fish" by name, or "none yet". */
    public synchronized String index(int max) {
        if (entries.isEmpty()) return "none yet";
        List<Entry> list = new ArrayList<>(entries.values());
        // By name, not by use: this text is part of a tool description, which must not change from thought to thought.
        list.sort(Comparator.comparing(x -> x.name));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() && i < max; i++) {
            Entry e = list.get(i);
            if (i > 0) sb.append("; ");
            sb.append(e.name);
            String p = e.params();
            if (!p.isEmpty()) sb.append('(').append(p).append(')');
            if (e.note != null) sb.append(": ").append(e.note);
        }
        if (list.size() > max) sb.append("; and ").append(list.size() - max).append(" more");
        return sb.toString();
    }
}
