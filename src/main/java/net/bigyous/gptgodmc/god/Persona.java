package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.bukkit.configuration.ConfigurationSection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * The god's identity: which archetype it is (chill, evil, benevolent, ... or one you write),
 * its name, how it talks, its dials and what it likes. Saved to disk so the same god returns
 * after restarts until `/god persona <archetype>` or `/god rebirth`.
 */
public final class Persona {
    public static final List<String> DIALS = List.of("generosity", "wrath", "chattiness", "humor", "meddling",
            "patience", "mercy", "pride", "curiosity", "vanity");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String archetype;
    public String name;
    public String epithet;
    /** What kind of god this is, from the archetype (plus the operator's extra text). */
    public String nature;
    /** How it talks. */
    public String speech;
    /** The invented flavor: temperament and history. */
    public String temperament;
    /** The name the god invented for itself at birth, when the operator gave it another one (then swapped out). */
    public String inventedName;
    public String voiceEffect;
    public Map<String, Integer> dials = new LinkedHashMap<>();
    public List<String> likes = new ArrayList<>();
    public List<String> dislikes = new ArrayList<>();
    public long bornAt;
    /** When the god last renamed itself (rename_self); 0 = never. */
    public long renamedAt;

    /** The identity of the god in question (see {@link Realm#current()}); null before it is born. */
    public static Persona current() {
        return Realm.current().persona;
    }

    public String shortName() {
        return name == null ? "God" : name;
    }

    public String title() {
        return epithet == null || epithet.isBlank() ? shortName() : shortName() + ", " + epithet;
    }

    public int dial(String key) {
        return dials.getOrDefault(key, 5);
    }

    private static Path file() {
        return Realm.current().file("persona.json");
    }

    /** Reads persona.json without contacting Claude. Safe on the server thread. */
    public static synchronized Persona loadSaved() {
        Path path = file();
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            Persona loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Persona.class);
            if (loaded != null && loaded.name != null) {
                fillMissingDials(loaded);
                applyConfigOverrides(loaded);
                if (loaded.inventedName == null && nameFixedByOperator()) {
                    loaded.inventedName = strayName(loaded.temperament, loaded.name);
                }
                String stray = loaded.inventedName;
                if (reconcileName(loaded)) {
                    // Its memories still call it by the invented name: they learn the chosen one.
                    try {
                        Memory.get().renameSelf(stray, loaded.name);
                    } catch (RuntimeException e) {
                        GPTGOD.LOGGER.warn("Could not update the god's memories to its new name", e);
                    }
                    save(loaded);
                }
                Realm.current().persona = loaded;
                return loaded;
            }
        } catch (IOException | RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not read persona.json, the god will be reborn", e);
        }
        return null;
    }

    /**
     * Loads the saved persona, or creates one. A new god is created when none exists or when
     * config.yml asks for a different archetype than the saved god has. May call Claude, so
     * run it off the server thread.
     */
    public static synchronized Persona loadOrCreate() {
        Realm realm = Realm.current();
        Persona saved = realm.persona != null ? realm.persona : loadSaved();
        String wanted = configuredArchetype();
        // The configured archetype is enforced once per server start, so a live /god persona sticks.
        boolean enforce = !realm.archetypeChecked;
        realm.archetypeChecked = true;
        if (saved != null && (!enforce || wanted.equals("random") || wanted.equalsIgnoreCase(saved.archetype))) {
            return saved;
        }
        return rebirth(null);
    }

    /** Creates a fresh god of the given archetype (null = config setting, "random" = any). */
    public static synchronized Persona rebirth(String archetypeOverride) {
        Realm realm = Realm.current();
        realm.archetypeChecked = true;
        String key = archetypeOverride == null ? configuredArchetype() : archetypeOverride.toLowerCase(Locale.ROOT);
        Map<String, ConfigurationSection> archetypes = archetypes();
        if (key.equals("random") || !archetypes.containsKey(key)) {
            if (!key.equals("random")) {
                GPTGOD.LOGGER.warn("Unknown archetype '" + key + "', choosing one at random. Known: " + archetypes.keySet());
            }
            List<String> keys = new ArrayList<>(archetypes.keySet());
            key = keys.isEmpty() ? "benevolent" : keys.get(new Random().nextInt(keys.size()));
        }
        ConfigurationSection a = archetypes.get(key);

        Persona p = new Persona();
        p.archetype = key;
        p.nature = a == null || a.getString("description") == null ? "A god of this world." : a.getString("description");
        String extra = GodSettings.personaExtra();
        if (extra != null && !extra.isBlank()) {
            p.nature = p.nature + " " + extra.trim();
        }
        p.speech = a == null || a.getString("speech") == null ? "" : a.getString("speech");
        p.voiceEffect = a == null || a.getString("voice-effect") == null ? "divine" : a.getString("voice-effect");
        for (String dial : DIALS) {
            p.dials.put(dial, Math.max(0, Math.min(10, a == null || !a.contains("dials." + dial) ? 5 : a.getInt("dials." + dial))));
        }
        pickBehaviors(p, a);

        // Mortals' names and the other gods' are taken: each god needs a name of its own.
        List<String> taken = new ArrayList<>(Memory.get().knownNames());
        for (Realm peer : realm.peers()) taken.add(peer.displayName());
        PersonaSpec spec = Chronicler.inventPersona(p, taken);
        fallbackName(p);
        for (int tries = 0; tries < 12 && clashes(p.name, realm); tries++) fallbackName(p);
        if (spec != null && spec.name() != null && clashes(spec.name(), realm)) {
            spec = new PersonaSpec(null, spec.epithet(), spec.temperament());
        }
        if (spec != null) {
            // An unfit invented name was refused: the table's name stands, the rest of the invention stays.
            if (spec.name() != null) p.name = spec.name();
            if (spec.epithet() != null && !spec.epithet().isBlank()) p.epithet = spec.epithet();
            p.temperament = spec.temperament() == null ? "" : spec.temperament().replace("{name}", p.name);
        }
        p.inventedName = p.name;
        p.bornAt = System.currentTimeMillis();
        applyConfigOverrides(p);
        reconcileName(p);
        realm.persona = p;
        save(p);
        // Its visual style, drawn the same way as its name: by the memory model, else from its archetype.
        try {
            String drawn = null;
            try {
                drawn = Chronicler.draftStyle(p);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Could not draw the god's style, using its archetype's", e);
            }
            FxStyle.born(realm, p, drawn);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not save the god's style", e);
        }
        Journal.add(Journal.Kind.INFO, "A god is born: " + p.title() + " (" + p.archetype + ")");
        return p;
    }

    /** The archetype this god should be: its realm's own setting, else personality.archetype. */
    public static String configuredArchetype() {
        String own = Realm.current().archetype;
        String v = own != null ? own : GPTGOD.get().getConfig().getString("personality.archetype", "random");
        return v == null || v.isBlank() ? "random" : v.trim().toLowerCase(Locale.ROOT);
    }

    public static Map<String, ConfigurationSection> archetypes() {
        Map<String, ConfigurationSection> out = new LinkedHashMap<>();
        ConfigurationSection root = GPTGOD.get().getConfig().getConfigurationSection("personality.archetypes");
        if (root != null) {
            for (String k : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(k);
                if (s != null) {
                    out.put(k.toLowerCase(Locale.ROOT), s);
                }
            }
        }
        return out;
    }

    public static void save(Persona p) {
        Path path = file();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save persona.json", e);
        }
    }

    /** A god born before a dial existed takes that dial from its archetype (or 5). */
    static void fillMissingDials(Persona p) {
        if (p.dials == null) p.dials = new LinkedHashMap<>();
        ConfigurationSection a = p.archetype == null ? null : archetypes().get(p.archetype);
        Map<String, Integer> next = new LinkedHashMap<>(p.dials);
        for (String dial : DIALS) {
            if (!next.containsKey(dial)) {
                next.put(dial, a == null || !a.contains("dials." + dial) ? 5
                        : Math.max(0, Math.min(10, a.getInt("dials." + dial))));
            }
        }
        p.dials = next;
    }

    /**
     * When the operator fixed the god's name, its self-description (written by the helper model with
     * the name it invented) is changed to use the fixed name. Returns true when anything changed.
     */
    static boolean reconcileName(Persona p) {
        String old = p.inventedName;
        if (old == null || old.isBlank() || p.name == null || old.equalsIgnoreCase(p.name)) {
            p.inventedName = null;
            return false;
        }
        p.temperament = swapName(p.temperament, old, p.name);
        p.inventedName = null;
        return true;
    }

    /** Replaces a whole-word name (any case of its first letter kept as written). */
    static String swapName(String text, String oldName, String newName) {
        if (text == null || oldName == null || oldName.isBlank()) return text;
        return text.replaceAll("\\b" + java.util.regex.Pattern.quote(oldName) + "\\b",
                java.util.regex.Matcher.quoteReplacement(newName));
    }

    private static final java.util.regex.Pattern SELF_NAME = java.util.regex.Pattern.compile(
            "\\bI am ([A-Z][\\p{L}'\\-]{2,15})\\b|^([A-Z][\\p{L}'\\-]{2,15}) (?:is|wants|demands|loves|hates|sees|weighs)\\b");

    /** A name the god calls itself in its self-description that is not its own, or null. */
    static String strayName(String temperament, String name) {
        if (temperament == null || name == null) return null;
        java.util.regex.Matcher m = SELF_NAME.matcher(temperament.trim());
        while (m.find()) {
            String found = m.group(1) != null ? m.group(1) : m.group(2);
            if (found != null && !found.equalsIgnoreCase(name) && !name.toLowerCase(java.util.Locale.ROOT)
                    .contains(found.toLowerCase(java.util.Locale.ROOT))) {
                return found;
            }
        }
        return null;
    }

    private static void applyConfigOverrides(Persona p) {
        Realm realm = Realm.current();
        if (realm.godName != null) {
            p.name = realm.godName;
        }
        if (!realm.isPrimary()) {
            return; // god.name and god.epithet describe the main god; other gods keep their own
        }
        String configured = GodSettings.configuredName();
        if (configured != null && !configured.isBlank()) {
            p.name = configured.trim();
        }
        String epithet = GPTGOD.get().getConfig().getString("god.epithet", "");
        if (epithet != null && !epithet.isBlank()) {
            p.epithet = epithet.trim();
        }
    }

    private static void pickBehaviors(Persona p, ConfigurationSection a) {
        int likeCount = Math.max(0, GodSettings.likedCount());
        int dislikeCount = Math.max(0, GodSettings.dislikedCount());
        List<String> likePool = a == null ? List.of() : a.getStringList("likes");
        List<String> dislikePool = a == null ? List.of() : a.getStringList("dislikes");
        List<String> general = new ArrayList<>(GodSettings.potentialBehaviors());
        Collections.shuffle(general);
        p.likes = pick(likePool, general, likeCount);
        general.removeAll(p.likes);
        List<String> remaining = new ArrayList<>(dislikePool);
        remaining.removeAll(p.likes);
        p.dislikes = pick(remaining, general, dislikeCount);
    }

    /** Takes from the archetype's own pool first, then tops up from the general list. */
    private static List<String> pick(List<String> preferred, List<String> general, int count) {
        List<String> pool = new ArrayList<>(preferred);
        Collections.shuffle(pool);
        List<String> out = new ArrayList<>();
        for (String s : pool) {
            if (out.size() >= count) break;
            out.add(s);
        }
        for (String s : general) {
            if (out.size() >= count) break;
            if (!out.contains(s)) out.add(s);
        }
        general.removeAll(out);
        return out;
    }

    /** True when the name is, contains or is inside another god's name in this world. */
    static boolean clashes(String name, Realm realm) {
        if (name == null) return false;
        for (Realm peer : realm.peers()) {
            String other = peer.displayName();
            if (other.equalsIgnoreCase(name) || Pantheon.names(name, other) || Pantheon.names(other, name)) return true;
        }
        return false;
    }

    /** A name and epithet from the archetype's table (see {@link GodNames}). */
    static void fallbackName(Persona p) {
        String[][] options = GodNames.fallbacks(p.archetype);
        String[] pick = options[new Random().nextInt(options.length)];
        p.name = pick[0];
        p.epithet = pick[1];
        p.temperament = "";
    }

    /** True when the operator fixed this god's name in config (then the god cannot rename itself). */
    static boolean nameFixedByOperator() {
        Realm realm = Realm.current();
        if (realm.godName != null) return true;
        String configured = realm.isPrimary() ? GodSettings.configuredName() : null;
        return configured != null && !configured.isBlank();
    }

    /**
     * The god takes a new name (and optionally a new epithet), at most once per
     * memory.rename-cooldown-hours. Returns null when done, else why not, phrased for the god.
     *
     * @param taken names that are not available (online mortals, mortals on record)
     */
    public static String rename(String name, String epithet, Collection<String> taken, long now) {
        // Not the class lock: rebirth holds that while it waits on the memory model, and this runs on the main thread.
        synchronized (RENAME_LOCK) {
            return renameLocked(name, epithet, taken, now);
        }
    }

    private static final Object RENAME_LOCK = new Object();

    private static String renameLocked(String name, String epithet, Collection<String> taken, long now) {
        Persona p = current();
        if (p == null) return "You have no identity yet to rename.";
        if (nameFixedByOperator()) {
            return "The server operator fixed your name as " + p.shortName() + "; you cannot change it.";
        }
        long cooldown = GodSettings.renameCooldownHours() * 3_600_000L;
        if (p.renamedAt > 0 && now - p.renamedAt < cooldown) {
            long hours = Math.max(1, (cooldown - (now - p.renamedAt) + 3_599_999L) / 3_600_000L);
            return "You took the name " + p.shortName() + " too recently; you may rename yourself again in about "
                    + hours + " hour(s).";
        }
        String n = name == null ? "" : name.trim();
        String problem = GodNames.problem(n, taken);
        if (problem != null) return problem;
        if (n.equalsIgnoreCase(p.name)) return "That is already your name.";
        String e = epithet == null ? null : epithet.replaceAll("[\\u00a7&][0-9a-fk-orA-FK-ORxX]", "")
                .replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (e != null && e.length() > 48) return "Keep the epithet to 48 characters.";
        p.name = n;
        if (e != null && !e.isEmpty()) p.epithet = e;
        p.renamedAt = now;
        save(p);
        return null;
    }

    /** What Claude fills in when inventing a new god. */
    public record PersonaSpec(String name, String epithet, String temperament) {
    }
}
