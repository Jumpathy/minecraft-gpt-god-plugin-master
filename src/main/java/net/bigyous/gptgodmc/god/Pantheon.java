package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * The gods created at runtime with /god create, who share a world with its first god. Kept in
 * plugins/gptgodmc/god/pantheon.json; each one's memory lives in god/realms/&lt;id&gt;/ like a realm god's.
 * Also holds the pure rules for several gods in one world: who is being addressed, who answers an
 * open prayer, and what the other gods see of a god's act.
 */
public final class Pantheon {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One created god as saved. */
    public static final class Entry {
        public String id;
        /** Id of the god whose worlds it shares ("main" for the server's first god). */
        public String domain;
        public String archetype;
        /** The name it was given, or null to let it invent one. */
        public String name;
        public long createdAt;
        public String createdBy;
        /** For a god created to rule a world no god ruled: that world's name(s). Its domain is its own id. */
        public List<String> worlds;
    }

    private static final class Book {
        int version = 1;
        List<Entry> gods = new ArrayList<>();
    }

    private static Path file;
    private static Book book = new Book();

    private Pantheon() {
    }

    // ---- Storage -------------------------------------------------------------------------------

    /** Reads pantheon.json (an empty pantheon when absent or unreadable). */
    static synchronized List<Entry> load(Path where) {
        file = where;
        book = new Book();
        if (where != null && Files.exists(where)) {
            try {
                Book read = GSON.fromJson(Files.readString(where, StandardCharsets.UTF_8), Book.class);
                if (read != null && read.gods != null) book = read;
            } catch (IOException | RuntimeException e) {
                GPTGOD.LOGGER.warn("Could not read " + where + "; no created gods were loaded", e);
            }
        }
        return new ArrayList<>(book.gods);
    }

    static synchronized void add(Entry e) {
        book.gods.removeIf(g -> g.id.equalsIgnoreCase(e.id));
        book.gods.add(e);
        save();
    }

    static synchronized void remove(String id) {
        if (book.gods.removeIf(g -> g.id.equalsIgnoreCase(id))) save();
    }

    static synchronized void setArchetype(String id, String archetype) {
        for (Entry e : book.gods) {
            if (e.id.equalsIgnoreCase(id)) {
                e.archetype = archetype;
                save();
            }
        }
    }

    /** True for a god made with /god create (only those can be removed in game). */
    public static synchronized boolean created(String id) {
        return book.gods.stream().anyMatch(g -> g.id.equalsIgnoreCase(id));
    }

    private static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(book), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save " + file, e);
        }
    }

    // ---- Names ---------------------------------------------------------------------------------

    /** A folder-safe id from a name ("Vorthak the Red" → "vorthak_the_red"), or "" when nothing is left. */
    public static String slug(String name) {
        if (name == null) return "";
        String s = name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "_").replaceAll("[^a-z0-9_-]", "");
        return s.length() > 24 ? s.substring(0, 24) : s;
    }

    /** Ids no created god may take. */
    static final Set<String> RESERVED = Set.of("main", "all", "everyone", "gods", "god", "global", "default");

    /** True when a word (or words) in the text is this name, on word boundaries and ignoring case. */
    public static boolean names(String text, String name) {
        if (text == null || name == null || name.isBlank()) return false;
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(name.trim()) + "(?![\\p{L}\\p{N}])")
                .matcher(text).find();
    }

    /**
     * Which god a prayer is aimed at: "@name rest", "name, rest", "name: rest" or a first word that is a
     * god's name. Returns the index into names and the words left, or index -1 (an open prayer).
     */
    public static Address addressed(List<String> names, String text) {
        String t = text == null ? "" : text.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        boolean at = lower.startsWith("@");
        String body = at ? lower.substring(1) : lower;
        int best = -1;
        int bestLen = 0;
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i) == null ? "" : names.get(i).toLowerCase(Locale.ROOT).trim();
            if (n.isEmpty() || n.length() <= bestLen || !body.startsWith(n)) continue;
            String after = body.substring(n.length());
            boolean boundary = after.isEmpty() || !Character.isLetterOrDigit(after.charAt(0));
            if (boundary) {
                best = i;
                bestLen = n.length();
            }
        }
        if (best < 0) return new Address(-1, t);
        String rest = t.substring((at ? 1 : 0) + bestLen).replaceFirst("^[\\s,:;!.-]+", "").trim();
        // "@Vorthak" with nothing after still names the god, but a plain first word needs words after it.
        if (rest.isEmpty() && !at) return new Address(best, t);
        return new Address(best, rest.isEmpty() ? t : rest);
    }

    public record Address(int index, String words) {
    }

    /**
     * Who answers words aimed at no god in particular: the god who favors the mortal most, ties going to
     * the god listed first (the world's first god).
     */
    public static int responder(int[] favor) {
        int best = 0;
        for (int i = 1; i < favor.length; i++) {
            if (favor[i] > favor[best]) best = i;
        }
        return favor.length == 0 ? -1 : best;
    }

    // ---- What other gods see -------------------------------------------------------------------

    /** Acts other gods never see: looking, remembering, the god's own bookkeeping. */
    static final Set<String> PRIVATE = Set.of("observe_player", "survey_area", "look", "remember", "judge", "recall",
            "search_annals", "rename_self", "command_done", "blueprints", "compose_song", "commune", "map", "see_build", "locate");

    /** Speech tools, seen as words in the other gods' conversation. */
    public static final Set<String> SPOKEN = Set.of("announce", "vessel_say");

    /**
     * What a god's peers perceive of one of its successful acts, or null for a private one. Whispers are
     * seen but not heard.
     */
    public static String witnessed(String god, String tool, JsonObject input, String resultText) {
        if (tool == null || PRIVATE.contains(tool)) return null;
        String who = god == null || god.isBlank() ? "Another god" : god;
        return switch (tool) {
            case "announce" -> who + " proclaimed to every mortal: \"" + clip(str(input, "message"), 160) + "\"";
            case "vessel_say" -> who + " said aloud through its vessel: \"" + clip(str(input, "message"), 160) + "\"";
            case "whisper" -> who + " whispered something to " + str(input, "player") + " (you did not hear what)";
            default -> {
                String what = resultText == null ? "" : resultText.replace('\n', ' ').trim();
                yield who + " used its power " + tool + (what.isEmpty() ? "" : ": " + clip(what, 160));
            }
        };
    }

    private static String str(JsonObject o, String key) {
        if (o == null) return "";
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    static String clip(String s, int max) {
        String t = s == null ? "" : s.replace('\n', ' ').trim();
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }
}
