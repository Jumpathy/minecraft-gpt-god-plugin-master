package net.bigyous.gptgodmc.god;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.WorldManager;
import net.bigyous.gptgodmc.god.vessel.Vessel;

/**
 * One god and the worlds it rules. A realm is a group of worlds (normally an overworld with its Nether
 * and End); each realm has its own persona, memory, event log, vessel and brain, and its god neither
 * sees nor touches anything outside it. Most servers have exactly one realm, "main".
 *
 * Code finds "the god in question" through {@link #current()}: a realm is bound to the thread while its
 * events, commands, thoughts and scheduled tasks run. Unbound code falls back to the main realm, so a
 * single-god server behaves as if realms did not exist.
 */
public final class Realm {
    private static final ThreadLocal<Realm> CURRENT = new ThreadLocal<>();
    private static final List<Realm> REALMS = new CopyOnWriteArrayList<>();
    /** Keys of the three vanilla dimensions, which always belong to the main realm unless claimed. */
    private static final Set<String> VANILLA = Set.of("minecraft:overworld", "minecraft:the_nether",
            "minecraft:the_end");
    private static volatile Realm primary;
    private static volatile boolean ruleAll;

    public final String id;
    /** Lower-case names of the worlds this god rules. */
    private final Set<String> worlds;
    /** Where this god's memory and persona are kept; null when running without a server (tests). */
    private final Path folder;
    /** Per-god overrides from realms.gods.<id>. A null archetype or look falls back to the server-wide setting. */
    /** May change when /god persona picks a new archetype for a created god (kept in pantheon.json). */
    public volatile String archetype;
    public final String godName;
    public final String look;
    /**
     * The id of the god whose worlds this one shares: its own id for the first god of a world, the
     * host's id for a god made with /god create.
     */
    public final String domain;
    /** When it was made with /god create (0 for config gods). */
    long createdAt;
    /** Set once /god remove took it away: nothing of it is saved any more. */
    volatile boolean removed;
    private volatile GodProfile profile;
    /** Thoughts in a row woken only by other gods (see God.beat), to stop gods talking forever. */
    volatile int divineStreak;

    private volatile Memory memory;
    volatile Persona persona;
    boolean archetypeChecked;
    private final EventLogger.Buffer events = new EventLogger.Buffer();
    /** What was said here lately, mortals and god together, in order. */
    private final Conversation conversation = new Conversation();
    private Vessel vessel;
    private GodBrain brain;
    /** When each custom power was last used here, by power name. */
    public final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    /** Counts this god's thoughts, for the usage log. */
    final AtomicInteger thoughts = new AtomicInteger();

    // Scheduling state, driven by God's heartbeat.
    volatile boolean awake;
    volatile boolean paused;
    volatile long attentionAt;
    volatile boolean attentionUrgent;
    volatile String attentionReason;
    long lastQuestTick;

    private Realm(String id, Set<String> worlds, Path folder, String archetype, String godName, String look) {
        this(id, worlds, folder, archetype, godName, look, id);
    }

    private Realm(String id, Set<String> worlds, Path folder, String archetype, String godName, String look,
            String domain) {
        this.domain = domain;
        this.id = id;
        this.worlds = worlds;
        this.folder = folder;
        this.archetype = archetype;
        this.godName = godName;
        this.look = look;
    }

    // ---- Setup ---------------------------------------------------------------------------------

    /** Builds the realms from config.yml. Called once when the plugin enables. */
    public static synchronized void configure() {
        FileConfiguration cfg = GPTGOD.get().getConfig();
        Path god = GPTGOD.get().getDataFolder().toPath().resolve("god");
        Set<String> mainWorlds = dimensionsOf(Bukkit.getWorlds().get(0).getName());
        for (String extra : cfg.getStringList("realms.extra-worlds")) {
            mainWorlds.add(extra.toLowerCase(Locale.ROOT));
        }
        List<Realm> fresh = new ArrayList<>();
        Realm main = new Realm("main", mainWorlds, god, null, null, null);
        fresh.add(main);

        ConfigurationSection gods = cfg.getConfigurationSection("realms.gods");
        if (gods != null) {
            for (String key : gods.getKeys(false)) {
                ConfigurationSection s = gods.getConfigurationSection(key);
                if (s == null) continue;
                List<String> names = s.getStringList("worlds");
                if (names.isEmpty()) names = List.of(key);
                Set<String> claimed = new LinkedHashSet<>();
                for (String name : names) {
                    if (s.getBoolean("include-dimensions", true)) claimed.addAll(dimensionsOf(name));
                    else claimed.add(name.toLowerCase(Locale.ROOT));
                }
                mainWorlds.removeAll(claimed);
                String id = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
                fresh.add(new Realm(id, claimed, god.resolve("realms").resolve(id), blankToNull(s.getString("archetype")),
                        blankToNull(s.getString("name")), blankToNull(s.getString("look"))));
                GPTGOD.LOGGER.info("Realm '" + id + "' has its own god over " + claimed);
            }
        }
        // Gods made with /god create, sharing the worlds of the god they were created beside.
        for (Pantheon.Entry e : Pantheon.load(god.resolve("pantheon.json"))) {
            Realm host = null;
            for (Realm r : fresh) {
                if (r.id.equalsIgnoreCase(e.domain)) host = r;
            }
            // A god created to rule a world of its own (one no god ruled when it was made).
            if (e.id != null && e.worlds != null && !e.worlds.isEmpty() && e.id.equalsIgnoreCase(e.domain)
                    && fresh.stream().noneMatch(r -> r.id.equalsIgnoreCase(e.id))) {
                Set<String> own = new java.util.LinkedHashSet<>();
                for (String w : e.worlds) own.add(w.toLowerCase(Locale.ROOT));
                Realm lord = new Realm(e.id, own, god.resolve("realms").resolve(e.id), e.archetype, e.name, null);
                lord.createdAt = e.createdAt;
                fresh.add(lord);
                GPTGOD.LOGGER.info("God '" + e.id + "' rules " + own);
                continue;
            }
            if (e.id == null || host == null || fresh.stream().anyMatch(r -> r.id.equalsIgnoreCase(e.id))) {
                GPTGOD.LOGGER.warn("Skipping created god '" + e.id + "': the god whose worlds it shared ('" + e.domain
                        + "') is gone, or the id is taken. Its folder is kept.");
                continue;
            }
            Realm co = new Realm(e.id, host.worlds, god.resolve("realms").resolve(e.id), e.archetype, e.name, null,
                    host.id);
            co.createdAt = e.createdAt;
            fresh.add(co);
            GPTGOD.LOGGER.info("God '" + e.id + "' shares the worlds of '" + host.id + "'");
        }
        REALMS.clear();
        REALMS.addAll(fresh);
        primary = main;
        ruleAll = cfg.getBoolean("realms.rule-all-worlds", false);
    }

    /** After /god reload: only the catch-all flag can change without a restart. */
    public static void reloadFlags() {
        ruleAll = GPTGOD.get().getConfig().getBoolean("realms.rule-all-worlds", false);
    }

    /** A world with the Nether and End that portal plugins pair with it (name_nether, name_the_end). */
    private static Set<String> dimensionsOf(String world) {
        String w = world.toLowerCase(Locale.ROOT);
        Set<String> out = new LinkedHashSet<>();
        out.add(w);
        out.add(w + "_nether");
        out.add(w + "_the_end");
        return out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ---- Lookup --------------------------------------------------------------------------------

    /** The realm bound to this thread, or the main realm when none is. Never null. */
    public static Realm current() {
        Realm r = CURRENT.get();
        return r != null ? r : primary();
    }

    public static Realm primary() {
        Realm p = primary;
        if (p == null) {
            synchronized (Realm.class) {
                if (primary == null) {
                    // No server (unit tests): a detached realm that lives in memory only.
                    primary = new Realm("main", new LinkedHashSet<>(), null, null, null, null);
                    REALMS.add(primary);
                }
                p = primary;
            }
        }
        return p;
    }

    public static List<Realm> all() {
        primary();
        return REALMS;
    }

    public static boolean several() {
        return REALMS.size() > 1;
    }

    public static Realm byId(String id) {
        for (Realm r : all()) {
            if (r.id.equalsIgnoreCase(id)) return r;
        }
        return null;
    }

    /**
     * The first god of this world (the one config.yml gives it), or null when no god has jurisdiction
     * there. Other gods may share the world: see {@link #godsOf}.
     */
    public static Realm of(World world) {
        if (world == null) return null;
        String name = world.getName().toLowerCase(Locale.ROOT);
        for (Realm r : all()) {
            if (r.isHost() && r.worlds.contains(name)) return r;
        }
        // A world a god made belongs to its maker (and the gods who share the maker's worlds): they see it,
        // hear it and may shape it. The labyrinth is a prison, not a domain, and stays as it was.
        net.bigyous.gptgodmc.god.worlds.GodWorlds.Made made = net.bigyous.gptgodmc.god.worlds.GodWorlds.of(world);
        if (made != null && !made.name().equals(net.bigyous.gptgodmc.god.worlds.Labyrinths.WORLD)) {
            Realm maker = byId(made.realm());
            if (maker != null) return maker.host();
        }
        Realm p = primary();
        if (ruleAll || VANILLA.contains(world.getKey().asString())) return p;
        if (GodSettings.roundsEnabled() && world.equals(WorldManager.getCurrentWorld())) return p;
        return null;
    }

    /** Every god of this world, its first god first; empty where no god rules. */
    public static List<Realm> godsOf(World world) {
        Realm host = of(world);
        return host == null ? List.of() : host.domainGods();
    }

    /** This god and every god sharing its worlds, the first god first. */
    public List<Realm> domainGods() {
        List<Realm> out = new ArrayList<>();
        for (Realm r : all()) {
            if (r.domain.equals(domain)) out.add(r);
        }
        return out;
    }

    /** The gods of these worlds that are alive (a god slain by the Godslayer is left out). */
    public List<Realm> livingDomainGods() {
        List<Realm> out = new ArrayList<>();
        for (Realm r : domainGods()) {
            if (!Godslayer.isSlain(r)) out.add(r);
        }
        return out;
    }

    /** The living gods of a world (see {@link #godsOf}). */
    public static List<Realm> livingGodsOf(World world) {
        List<Realm> out = new ArrayList<>();
        for (Realm r : godsOf(world)) {
            if (!Godslayer.isSlain(r)) out.add(r);
        }
        return out;
    }

    /** The other gods of this god's worlds. */
    public List<Realm> peers() {
        List<Realm> out = domainGods();
        out.remove(this);
        return out;
    }

    /** True when other gods share this god's worlds. */
    public boolean shared() {
        for (Realm r : all()) {
            if (r != this && r.domain.equals(domain)) return true;
        }
        return false;
    }

    /** The first god of a world (not one made with /god create beside it). */
    public boolean isHost() {
        return domain.equals(id);
    }

    /** The first god of this god's worlds. */
    public Realm host() {
        Realm h = byId(domain);
        return h != null ? h : this;
    }

    public boolean governs(World world) {
        Realm h = of(world);
        return h != null && h.domain.equals(domain);
    }

    /** The god's name now (its persona's short name), else its given name, else its id. */
    public String displayName() {
        Persona p = persona;
        if (p != null) return p.shortName();
        return godName != null ? godName : id;
    }

    /**
     * A god by id, by name (its short name or full name) or by a unique prefix of either, ignoring
     * case and a leading @. Null when none or several match.
     */
    public static Realm byName(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("@")) t = t.substring(1);
        if (t.isEmpty()) return null;
        Realm exact = byId(t);
        if (exact != null) return exact;
        String lower = t.toLowerCase(Locale.ROOT);
        Realm prefix = null;
        int matches = 0;
        for (Realm r : all()) {
            Persona p = r.persona;
            String shortName = r.displayName().toLowerCase(Locale.ROOT);
            String full = p == null || p.name == null ? null : p.name.toLowerCase(Locale.ROOT);
            if (lower.equals(shortName) || lower.equals(full)) return r;
            if (r.id.startsWith(lower) || shortName.startsWith(lower)) {
                prefix = r;
                matches++;
            }
        }
        return matches == 1 ? prefix : null;
    }

    // ---- Per-god settings ----------------------------------------------------------------------

    /** This god's own settings (profile.json). */
    public GodProfile profile() {
        GodProfile p = profile;
        if (p == null) {
            synchronized (this) {
                if (profile == null) profile = GodProfile.open(file("profile.json"));
                p = profile;
            }
        }
        return p;
    }

    /** How mighty this god is: its profile's setting, else supreme for a world's first god, else the default. */
    public Power power() {
        Power set = Power.parse(profile().power);
        if (set != null) return set;
        if (isHost()) return Power.SUPREME;
        Power d = Power.parse(GodSettings.pantheonDefaultPower());
        return d != null ? d : Power.GREATER;
    }

    /** The vessel look: this god's own choice, else its realms.gods entry, else vessel.look. */
    /** Turned off by the operator (God Manager): it neither thinks nor takes form. */
    public boolean dormant() {
        return Boolean.TRUE.equals(profile().dormant);
    }

    public String lookSetting() {
        String own = GodProfile.blankToNull(profile().look);
        if (own != null) return own.toLowerCase(Locale.ROOT);
        return look != null ? look : GodSettings.vesselLook();
    }

    /** False once it has spent its thoughts in a row answering only gods (pantheon.divine-exchanges). */
    public boolean mayBeWokenByGods() {
        return awake && !paused && divineStreak < GodSettings.pantheonDivineExchanges();
    }

    /** The god's identity, or null before it is born. */
    public Persona persona() {
        return persona;
    }

    /** A created god's archetype, remembered across restarts. */
    public void setArchetype(String key) {
        archetype = key;
        Pantheon.setArchetype(id, key);
    }

    public boolean isRemoved() {
        return removed;
    }

    // ---- Creating and removing gods ------------------------------------------------------------

    /**
     * Makes a new god in the worlds of `host`, saved in pantheon.json. Server thread.
     *
     * @param name      the name to give it, or null to let it invent one
     * @param archetype a personality.archetypes key, "random", or null for personality.archetype
     * @param power     lesser | greater | supreme, or null for pantheon.default-power
     * @throws IllegalArgumentException with a message for the operator when it cannot be made
     */
    public static synchronized Realm create(Realm host, String name, String archetype, String power, String by) {
        Realm home = host.host();
        int max = GodSettings.pantheonMaxGods();
        if (home.livingDomainGods().size() >= max) { // the dead do not count
            throw new IllegalArgumentException("These worlds already have " + max + " gods (pantheon.max-gods).");
        }
        String clean = name == null ? null : name.trim();
        if (clean != null && clean.isEmpty()) clean = null;
        if (clean != null) {
            String problem = GodNames.operatorProblem(clean); // the operator may pick any one-word name
            if (problem != null) throw new IllegalArgumentException(problem);
            for (Realm r : all()) {
                String other = r.displayName();
                if (other.equalsIgnoreCase(clean) || Pantheon.names(clean, other) || Pantheon.names(other, clean)) {
                    throw new IllegalArgumentException("A god named " + other + " already exists; names must not "
                            + "contain one another.");
                }
            }
        }
        String base = clean != null ? Pantheon.slug(clean) : "";
        if (base.isEmpty() || Pantheon.RESERVED.contains(base)) base = "god";
        String id = base;
        for (int n = 2; byId(id) != null || folderTaken(home, id) || Pantheon.RESERVED.contains(id); n++) {
            id = base + n;
        }
        Path folder = home.folder == null ? null : godFolder(home).resolve("realms").resolve(id);
        Realm r = new Realm(id, home.worlds, folder, blankToNull(archetype), clean, null, home.id);
        r.createdAt = System.currentTimeMillis();
        Power p = Power.parse(power);
        if (p != null) r.profile().power = p.id;
        // A look and a voice its peers do not have, so mortals can tell the gods apart.
        r.profile().look = freeLook(home, archetype);
        r.profile().voice = freeVoice(home);
        r.profile().save();
        Pantheon.Entry e = new Pantheon.Entry();
        e.id = id;
        e.domain = home.id;
        e.archetype = blankToNull(archetype);
        e.name = clean;
        e.createdAt = r.createdAt;
        e.createdBy = by;
        if (folder != null) Pantheon.add(e);
        REALMS.add(r);
        return r;
    }

    /**
     * Makes a god for a world that no god rules (a Multiverse world outside every realm): it is that
     * world's first god, with its own realm, and further gods can be created beside it there.
     */
    public static synchronized Realm found(World world, String name, String archetype, String power, String by) {
        if (of(world) != null) throw new IllegalArgumentException("A god already rules " + world.getName() + ".");
        Realm first = primary();
        String clean = name == null ? null : name.trim();
        if (clean != null && clean.isEmpty()) clean = null;
        if (clean != null) {
            String problem = GodNames.operatorProblem(clean);
            if (problem != null) throw new IllegalArgumentException(problem);
            for (Realm r : all()) {
                String other = r.displayName();
                if (other.equalsIgnoreCase(clean) || Pantheon.names(clean, other) || Pantheon.names(other, clean)) {
                    throw new IllegalArgumentException("A god named " + other + " already exists; names must not "
                            + "contain one another.");
                }
            }
        }
        String base = clean != null ? Pantheon.slug(clean) : Pantheon.slug(world.getName());
        if (base.isEmpty() || Pantheon.RESERVED.contains(base)) base = "god";
        String id = base;
        for (int n = 2; byId(id) != null || folderTaken(first, id) || Pantheon.RESERVED.contains(id); n++) {
            id = base + n;
        }
        Set<String> own = new java.util.LinkedHashSet<>();
        own.add(world.getName().toLowerCase(Locale.ROOT));
        Path folder = first.folder == null ? null : godFolder(first).resolve("realms").resolve(id);
        Realm r = new Realm(id, own, folder, blankToNull(archetype), clean, null);
        r.createdAt = System.currentTimeMillis();
        Power p = Power.parse(power);
        if (p != null) r.profile().power = p.id;
        r.profile().save();
        Pantheon.Entry e = new Pantheon.Entry();
        e.id = id;
        e.domain = id;
        e.archetype = blankToNull(archetype);
        e.name = clean;
        e.createdAt = r.createdAt;
        e.createdBy = by;
        e.worlds = new ArrayList<>(own);
        if (folder != null) Pantheon.add(e);
        REALMS.add(r);
        return r;
    }

    /** The god/ folder (main keeps its files there; a realm god in god/realms/<id>/). */
    private static Path godFolder(Realm home) {
        return home.isPrimary() ? home.folder : home.folder.getParent().getParent();
    }

    private static boolean folderTaken(Realm home, String id) {
        if (home.folder == null) return false;
        return java.nio.file.Files.exists(godFolder(home).resolve("realms").resolve(id));
    }

    /** A look that suits the new god's archetype (best first) and that none of its peers wears. */
    private static String freeLook(Realm home, String archetype) {
        Set<String> used = new java.util.HashSet<>();
        for (Realm g : home.domainGods()) used.add(g.lookSetting());
        for (String l : net.bigyous.gptgodmc.god.vessel.VesselLook.suited(archetype)) {
            if (!used.contains(l)) return l;
        }
        for (String l : net.bigyous.gptgodmc.god.vessel.VesselLook.NAMES) {
            if (!used.contains(l)) return l;
        }
        return null;
    }

    private static String freeVoice(Realm home) {
        Set<String> used = new java.util.HashSet<>();
        for (Realm g : home.domainGods()) {
            String v = GodProfile.blankToNull(g.profile().voice);
            used.add(v != null ? v : GodSettings.configuredTtsVoice());
        }
        for (String v : GodSettings.pantheonVoices()) {
            if (!used.contains(v)) return v;
        }
        return null;
    }

    /**
     * Takes a created god away for good: its thoughts stop, its body vanishes, its pending commands are
     * dropped, and its folder moves to god/removed/<id>-<time>/ (never deleted). Server thread.
     *
     * @return null when done, else why not
     */
    public static synchronized String remove(Realm r) {
        if (r.isPrimary()) return "The server's first god cannot be removed (use /god persona to replace it).";
        if (!Pantheon.created(r.id)) {
            return r.id + " comes from config.yml (realms.gods); remove it there.";
        }
        r.awake = false;
        REALMS.remove(r);
        r.run(r::shutdown);
        r.removed = true;
        Edicts.discard(r);
        Memory m = r.memory;
        if (m != null) m.retire();
        GodProfile prof = r.profile;
        if (prof != null) prof.detach();
        Pantheon.remove(r.id);
        if (r.folder != null && java.nio.file.Files.isDirectory(r.folder)) {
            Annals.close(r.folder);
            String stamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path to = godFolder(primary()).resolve("removed").resolve(r.id + "-" + stamp);
            try {
                java.nio.file.Files.createDirectories(to.getParent());
                java.nio.file.Files.move(r.folder, to);
            } catch (java.io.IOException e) {
                GPTGOD.LOGGER.warn("Could not move " + r.folder + " to " + to + "; it stays where it is", e);
            }
        }
        return null;
    }

    /** A god kept in memory only, sharing the worlds of `host` (tests). */
    static Realm detachedPeer(Realm host, String id) {
        Realm r = new Realm(id, host.worlds, null, null, null, null, host.id);
        REALMS.add(r);
        return r;
    }

    /** Drops a god added by {@link #detachedPeer} (tests). */
    static void dropDetached(Realm r) {
        REALMS.remove(r);
    }

    public boolean isPrimary() {
        return this == primary;
    }

    /** This realm's home world (its overworld, or the round world for the main realm). */
    public World world() {
        if (isPrimary() && GodSettings.roundsEnabled()) return WorldManager.getCurrentWorld();
        World fallback = null;
        for (World w : Bukkit.getWorlds()) {
            if (!governs(w)) continue;
            if (w.getEnvironment() == World.Environment.NORMAL) return w;
            if (fallback == null) fallback = w;
        }
        return fallback != null ? fallback : Bukkit.getWorlds().get(0);
    }

    /** Every loaded world this god rules, home world first. */
    public List<World> worldList() {
        List<World> out = new ArrayList<>();
        World home = world();
        out.add(home);
        for (World w : Bukkit.getWorlds()) {
            if (governs(w) && !w.equals(home)) out.add(w);
        }
        return out;
    }

    /** The mortals currently inside this realm. */
    public List<Player> players() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (governs(p.getWorld())) out.add(p);
        }
        return out;
    }

    // ---- Binding -------------------------------------------------------------------------------

    /** Runs work with this realm as the current one, then restores whatever was bound before. */
    public void run(Runnable work) {
        Realm before = CURRENT.get();
        CURRENT.set(this);
        try {
            work.run();
        } finally {
            if (before == null) CURRENT.remove();
            else CURRENT.set(before);
        }
    }

    public <T> T call(Sync.WorldCall<T> work) throws Exception {
        Realm before = CURRENT.get();
        CURRENT.set(this);
        try {
            return work.call();
        } finally {
            if (before == null) CURRENT.remove();
            else CURRENT.set(before);
        }
    }

    /** Like {@link #call} for work that throws nothing checked. */
    public <T> T get(java.util.function.Supplier<T> work) {
        Realm before = CURRENT.get();
        CURRENT.set(this);
        try {
            return work.get();
        } finally {
            if (before == null) CURRENT.remove();
            else CURRENT.set(before);
        }
    }

    /** Binds this realm to the calling thread for good (a god's own worker threads). */
    void bindThread() {
        CURRENT.set(this);
    }

    /** Wraps work so it runs later under the realm that is current right now. */
    public static Runnable bound(Runnable work) {
        Realm r = current();
        return () -> r.run(work);
    }

    public static BukkitTask soon(Runnable work) {
        return Bukkit.getScheduler().runTask(GPTGOD.get(), bound(work));
    }

    public static BukkitTask later(Runnable work, long ticks) {
        return Bukkit.getScheduler().runTaskLater(GPTGOD.get(), bound(work), ticks);
    }

    public static BukkitTask timer(Runnable work, long delay, long period) {
        return Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), bound(work), delay, period);
    }

    // ---- What belongs to this god --------------------------------------------------------------

    /** A file in this god's folder, or null when nothing is persisted (tests). */
    Path file(String name) {
        return folder == null || removed ? null : folder.resolve(name);
    }

    public Conversation conversation() {
        return conversation;
    }

    public Memory memory() {
        Memory m = memory;
        if (m == null) {
            synchronized (this) {
                if (memory == null) memory = Memory.open(file("memory.json"));
                m = memory;
            }
        }
        return m;
    }

    synchronized void setMemory(Memory fresh) {
        memory = fresh;
    }

    public EventLogger.Buffer events() {
        return events;
    }

    public synchronized Vessel vessel() {
        if (vessel == null) vessel = new Vessel(this);
        return vessel;
    }

    public synchronized GodBrain brain() {
        if (brain == null) brain = new GodBrain(this);
        return brain;
    }

    /** The mind if it has started thinking at least once, else null (does not start one). */
    synchronized GodBrain brainIfStarted() {
        return brain;
    }

    /** The body if it was ever created, else null. */
    synchronized Vessel vesselIfCreated() {
        return vessel;
    }

    /** Stops this god's threads and removes its body. Called when the plugin disables. */
    synchronized void shutdown() {
        if (vessel != null) vessel.shutdown();
        if (brain != null) brain.shutdown();
        if (memory != null) memory.save();
    }

    @Override
    public String toString() {
        return id;
    }
}
