package net.bigyous.gptgodmc.god;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.loggables.SystemInjectionLoggable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The front door to "the god". There may be several — one per {@link Realm} (a group of worlds) —
 * and every method here speaks to the god of the current realm: the one whose world the calling
 * code is acting in. One heartbeat on the server thread decides, four times a second, whether each
 * god should look at its world now.
 */
public final class God {
    private static BukkitTask heartbeat;
    private static boolean warnedNoKey = false;

    private God() {
    }

    // ---- The current god -----------------------------------------------------------------------

    public static Vessel vessel() {
        return Realm.current().vessel();
    }

    public static GodBrain brain() {
        return Realm.current().brain();
    }

    public static boolean isAwake() {
        return Realm.current().awake;
    }

    public static boolean isPaused() {
        return Realm.current().paused;
    }

    public static void setPaused(boolean value) {
        Realm.current().paused = value;
        Journal.add(Journal.Kind.INFO, value ? "Thoughts paused by the operator" : "Thoughts resumed");
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    public static void enable() {
        Realm.configure();
        for (Realm r : Realm.all()) {
            r.run(() -> {
                Memory.load();
                Persona.loadSaved();
            });
        }
        Vessel.cleanupStrays();
        heartbeat = Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), God::beatAll, 40L, 5L);
    }

    public static void disable() {
        if (heartbeat != null) {
            heartbeat.cancel();
        }
        for (Realm r : Realm.all()) {
            r.run(r::shutdown);
        }
        Chronicler.shutdown();
        GodClient.close();
    }

    /**
     * Wakes every god whose worlds now hold a mortal and puts the others to sleep. Call after a
     * join, a quit, or someone stepping through to a world another god rules.
     */
    public static void refreshPresence() {
        for (Realm r : Realm.all()) {
            boolean anyone = !r.players().isEmpty();
            if (anyone && !r.awake) r.run(God::awaken);
            else if (!anyone && r.awake) r.run(God::sleep);
        }
    }

    /** The first mortal arrived in the current god's worlds. */
    public static void awaken() {
        Realm r = Realm.current();
        if (r.awake) return;
        r.awake = true;
        Journal.add(Journal.Kind.INFO, "The god awakens");
        if (GodClient.hasCredentials()) {
            r.brain().submit(() -> {
                Persona.loadOrCreate();
                r.vessel().refreshNameplateLater();
            });
        }
        // By default the god begins formless; it takes a body when summoned, angered, or when it wishes.
        if (GodSettings.vesselManifestOnWake() && GodSettings.vesselEnabled()) {
            Realm.later(() -> {
                Vessel vessel = r.vessel();
                for (Player p : r.players()) {
                    if (p.getGameMode() != GameMode.SPECTATOR && !vessel.isManifested()) {
                        vessel.follow(p);
                        vessel.stay();
                        break;
                    }
                }
            }, 100L);
        }
        requestAttention("mortals have entered your world");
    }

    /** Everyone left the current god's worlds (or the round reset). */
    public static void sleep() {
        Realm r = Realm.current();
        if (!r.awake) return;
        r.awake = false;
        r.attentionAt = 0;
        r.attentionUrgent = false;
        Vessel vessel = r.vesselIfCreated();
        if (vessel != null) vessel.vanish();
        r.memory().save();
        Journal.add(Journal.Kind.INFO, "The god sleeps");
    }

    // ---- Attention -----------------------------------------------------------------------------

    /** Something a god would notice soon (a death, a great deed). */
    public static void requestAttention(String reason) {
        requestAttention(reason, false);
    }

    /**
     * Asks the current god to look at its world. `urgent` is for mortals addressing it directly
     * (a prayer, its name, a touch): those are answered within a blink instead of a few seconds.
     * Safe from any thread, and safe without a server.
     */
    public static void requestAttention(String reason, boolean urgent) {
        Realm r = Realm.current();
        String had = r.attentionReason;
        if (r.attentionAt == 0) {
            r.attentionReason = reason;
            r.attentionAt = System.currentTimeMillis();
        } else if (had == null) {
            r.attentionReason = reason;
        } else if (reason != null && !had.contains(reason) && had.length() < 300) {
            r.attentionReason = had + "; " + reason;
        }
        if (urgent) r.attentionUrgent = true;
    }

    /** The mind has just been shown everything that was pending, so nothing is waiting any more. */
    static void attentionServed(Realm r) {
        r.attentionAt = 0;
        r.attentionReason = null;
        r.attentionUrgent = false;
    }

    /** Operator: look now, optionally with an instruction. */
    public static void wake(String directive) {
        Realm r = Realm.current();
        if (directive != null && !directive.isBlank()) {
            EventLogger.addLoggable(new SystemInjectionLoggable(directive));
        }
        GodBrain brain = r.brainIfStarted();
        if (brain != null) brain.clearBackoff();
        requestAttention("the server operator summoned you", true);
        r.attentionAt = 1;
    }

    /**
     * An instant, local sign that the god heard a mortal — before its real answer arrives a moment
     * later. The vessel turns to face them; they hear a soft chime and see a line above the hotbar.
     * Costs nothing: no request is made here. Server thread only.
     */
    public static void acknowledge(Player player) {
        acknowledge(Realm.of(player.getWorld()), player);
    }

    /** Like {@link #acknowledge(Player)} for one god in particular (the one the mortal spoke to). */
    public static void acknowledge(Realm r, Player player) {
        if (r == null || !r.awake || r.paused || r.dormant() || Godslayer.isSlain(r)) return;
        Vessel vessel = r.vesselIfCreated();
        if (vessel != null && vessel.isManifested()) {
            Location at = vessel.location();
            if (at != null && at.getWorld().equals(player.getWorld()) && GodSettings.faceSpeakers()) {
                vessel.faceSpeaker(player);
            }
        }
        if (!GodSettings.acknowledge() || !GodSettings.brainEnabled() || !"live".equals(GodSettings.brainMode())
                || !GodClient.hasCredentials()) {
            return;
        }
        String name = r.get(() -> {
            Persona p = Persona.current();
            return p == null ? "The god" : p.shortName();
        });
        player.sendActionBar(Component.text("✦ " + name + " turns its gaze upon you ✦", Speech.VIOLET));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.6f);
    }

    // ---- Heartbeat -----------------------------------------------------------------------------

    /** When any god last began a thought nobody asked for (several gods take turns, a few seconds apart). */
    private static long lastUnprompted;

    private static void beatAll() {
        java.util.List<Realm> all = Realm.all();
        Realm muser = idleTurn(all, System.currentTimeMillis());
        for (Realm r : all) {
            r.run(() -> beat(r, all.size() == 1 || r == muser));
        }
    }

    /**
     * With several gods, only one muses in a quiet moment: of those due, the one that has waited longest.
     * The others keep their turn for later, so idle cost does not grow with the number of gods.
     */
    private static Realm idleTurn(java.util.List<Realm> all, long now) {
        if (GodSettings.idleSeconds() <= 0) return null;
        Realm best = null;
        long oldest = Long.MAX_VALUE;
        for (Realm r : all) {
            GodBrain b = r.brainIfStarted();
            long ended = b == null ? 0 : b.lastThoughtEnded();
            int idle = museSeconds(r);
            if (!r.awake || r.paused || (b != null && b.isThinking()) || now - ended < idle * 1000L) continue;
            if (ended < oldest) {
                oldest = ended;
                best = r;
            }
        }
        return best;
    }

    /**
     * Seconds between quiet thoughts: brain.idle-seconds, or sooner while a pastime is underway (free will),
     * so it keeps at what it chose to do.
     */
    static int museSeconds(Realm r) {
        int idle = GodSettings.idleSeconds();
        Initiative.Level level = GodSettings.initiativeLevel();
        if (level.paceSeconds <= 0) return idle;
        try {
            if (Initiative.current(r.memory()) == null && level != Initiative.Level.FREE) return idle;
        } catch (RuntimeException e) {
            return idle;
        }
        return Math.min(idle, level.paceSeconds * (Initiative.current(r.memory()) == null ? 2 : 1));
    }

    /** How many gods are thinking right now. */
    private static int thinkingNow() {
        int n = 0;
        for (Realm r : Realm.all()) {
            GodBrain b = r.brainIfStarted();
            if (b != null && b.isThinking()) n++;
        }
        return n;
    }

    /** True when every reason that woke the god came from another god (not a mortal or an operator). */
    static boolean divineOnly(String reason) {
        if (reason == null || reason.isBlank()) return false;
        for (String part : reason.split("; ")) {
            if (!part.startsWith(net.bigyous.gptgodmc.loggables.DivineActLoggable.DIVINE)) return false;
        }
        return true;
    }

    private static final java.util.Map<String, Long> lastWorkingNote = new java.util.HashMap<>();

    /** While it thinks, everyone waiting on a binding command sees, above their hotbar, that it is at work. */
    private static void showWorking(Realm r, long now) {
        Long last = lastWorkingNote.get(r.id);
        if (last != null && now - last < 1000) return;
        lastWorkingNote.put(r.id, now);
        Persona p = Persona.current();
        String name = p == null ? "The god" : p.shortName();
        String[] dots = { ".", "..", "..." };
        String tail = dots[(int) ((now / 1000) % 3)];
        for (Edicts.Edict e : Edicts.book().pending()) {
            if (e.standing() || e.issuerId == null) continue;
            try {
                Player pl = Bukkit.getPlayer(java.util.UUID.fromString(e.issuerId));
                if (pl != null && pl.isOnline()) {
                    pl.sendActionBar(Component.text("\u2726 " + name + " is working on your command #" + e.number
                            + tail, NamedTextColor.LIGHT_PURPLE));
                }
            } catch (IllegalArgumentException ignored) {
                // a damaged UUID
            }
        }
    }

    private static void beat(Realm r, boolean mayMuse) {
        long now = System.currentTimeMillis();
        if (now - r.lastQuestTick > 20_000) {
            r.lastQuestTick = now;
            QuestBoard.tick();
        }
        try {
            PurposeBar.tick(r, now);
        } catch (RuntimeException e) {
            // a bar must never stop the heartbeat
        }
        if (!r.awake || r.paused || r.dormant() || !GodSettings.brainEnabled()) return;
        if (Godslayer.isSlain(r)) return; // the dead do not think
        if (!"live".equals(GodSettings.brainMode())) return;
        if (!GodClient.hasCredentials()) {
            if (!warnedNoKey) {
                warnedNoKey = true;
                warnOperators();
            }
            return;
        }
        warnedNoKey = false;
        GodBrain brain = r.brain();
        if (brain.isThinking()) showWorking(r, now);
        if (brain.isThinking() || brain.isBackingOff()) return;

        long sinceLast = now - brain.lastThoughtEnded();
        long waited = now - r.attentionAt;
        String reason = null;
        if (r.attentionAt != 0 && (r.attentionUrgent
                // Spoken to directly: answer as soon as the words have landed.
                ? waited >= GodSettings.reactionDelayMs() && sinceLast >= 1000 && !Guard.pending(now)
                // Something notable happened: let a second of events gather, then look.
                : waited >= 1500 && sinceLast >= GodSettings.minGapSeconds() * 1000L)) {
            reason = r.attentionReason == null ? "something caught your attention" : r.attentionReason;
        } else if (r.events().hasPending() && sinceLast >= GodSettings.heartbeatSeconds() * 1000L) {
            reason = r.attentionReason != null ? r.attentionReason : "your regular watch over the world";
        } else if (mayMuse && GodSettings.idleSeconds() > 0 && sinceLast >= museSeconds(r) * 1000L
                && !r.players().isEmpty()) {
            reason = Initiative.current(r.memory()) != null && GodSettings.initiativeLevel() != Initiative.Level.WATCH
                    ? "a quiet moment; nothing demands you, so carry on with your pastime or follow a new whim"
                    : "a quiet moment; nothing demands you, but you may act on your own whims";
        }
        if (reason == null) return;
        boolean urgent = r.attentionAt != 0 && r.attentionUrgent;
        if (!urgent) {
            // Several gods: a few at a time, and not all at the same moment.
            if (thinkingNow() >= GodSettings.pantheonMaxConcurrent()) return;
            if (Realm.several() && now - lastUnprompted < GodSettings.pantheonStaggerSeconds() * 1000L) return;
            // Over its hourly budget, a god answers only those who call on it directly.
            if (Spending.weary(r.id, GodSettings.pantheonBudgetPerGod(), now)) return;
        }
        boolean divine = divineOnly(r.attentionReason);
        r.divineStreak = divine ? r.divineStreak + 1 : 0;
        if (!urgent) lastUnprompted = now;
        attentionServed(r);
        brain.think(reason);
    }

    private static void warnOperators() {
        String msg = Llm.anthropic()
                ? "The god slumbers: no Anthropic API key. Run `gptgod setkey <key>` in the server console, "
                        + "or set ANTHROPIC_API_KEY."
                : "The god slumbers: " + Llm.notReady();
        GPTGOD.LOGGER.warn(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) {
                p.sendMessage(Component.text("[GPTGOD] " + msg, NamedTextColor.GOLD));
            }
        }
    }
}
