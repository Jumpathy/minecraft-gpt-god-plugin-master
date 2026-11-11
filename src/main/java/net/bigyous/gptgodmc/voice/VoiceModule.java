package net.bigyous.gptgodmc.voice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.loggables.ChatLoggable;

/**
 * The god's ears and voice. Everything that touches Simple Voice Chat lives in
 * {@link VoicechatBridge}, which is only loaded when that plugin is installed, so this
 * plugin works without it.
 */
public final class VoiceModule {
    private static VoiceBackend backend;
    private static final Transcriber transcriber = new Transcriber();
    private static final Synthesizer synthesizer = new Synthesizer();
    private static final ExecutorService ears = Executors.newSingleThreadExecutor(r -> daemon(r, "god-ears"));
    private static final ExecutorService voice = Executors.newSingleThreadExecutor(r -> daemon(r, "god-voice"));
    private static final VoiceModule INSTANCE = new VoiceModule();

    private VoiceModule() {
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    public static void enable() {
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            GPTGOD.LOGGER.info("Simple Voice Chat is not installed: the god will not hear voices or speak aloud.");
            return;
        }
        try {
            backend = (VoiceBackend) Class.forName("net.bigyous.gptgodmc.voice.VoicechatBridge")
                    .getDeclaredConstructor().newInstance();
            backend.register(INSTANCE);
            GPTGOD.LOGGER.info("Voice enabled: listening with " + GodSettings.transcriptionEngine()
                    + " transcription, speaking with " + GodSettings.ttsEngine() + " TTS.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            backend = null;
            GPTGOD.LOGGER.warn("Could not hook into Simple Voice Chat; voice features are off.", e);
        }
    }

    public static boolean isActive() {
        return backend != null;
    }

    public static void shutdown() {
        if (backend != null) {
            backend.shutdown();
        }
        ears.shutdownNow();
        voice.shutdownNow();
        transcriber.close();
    }

    // ---- Hearing ----------------------------------------------------------------------------------

    /** Called by the bridge when a player stops talking. */
    void heard(Player player, short[] pcm48k, Instant startedAt) {
        if ("off".equals(GodSettings.transcriptionEngine())) return;
        String name = player.getName();
        // Heard by the god of the world the speaker stands in; nobody listens in a godless world.
        java.util.List<Realm> gods = Realm.godsOf(player.getWorld());
        if (gods.isEmpty()) return;
        ears.execute(() -> {
            try {
                String text = transcriber.transcribe(pcm48k);
                if (text != null) {
                    // Transcribed once; every god of the world hears it and decides if it was spoken to.
                    for (Realm realm : gods) {
                        realm.run(() -> {
                            Journal.add(Journal.Kind.INFO, name + " said aloud: " + text);
                            EventLogger.addLoggable(new ChatLoggable(name, text, startedAt, true));
                        });
                        if (realm.get(() -> ChatLoggable.addressesGod(name, text)) && GPTGOD.get().isEnabled()) {
                            Bukkit.getScheduler().runTask(GPTGOD.get(), () -> God.acknowledge(realm, player));
                        }
                    }
                }
            } catch (Exception e) {
                GPTGOD.LOGGER.warn("Transcription failed: " + e.getMessage());
            }
        });
    }

    // ---- Speaking ---------------------------------------------------------------------------------

    /** Speaks into the minds of these players (whispers, announcements). Any thread. */
    public static void speak(String text, List<Player> players) {
        speak(text, players, null);
    }

    /** @param tone a voice effect the god chose for this line, or null to follow its mood */
    public static void speak(String text, List<Player> players, String tone) {
        speak(text, players, tone, null);
    }

    /**
     * @param show puts the line's text in front of the mortals (chat, title). It is run exactly once, on
     *             the server thread: when the voice starts, so words and voice arrive together, or after
     *             voice.tts.text-wait-ms if the voice is slower than that, or at once when nobody would
     *             hear the voice anyway. May be null.
     */
    public static void speak(String text, List<Player> players, String tone, Runnable show) {
        speak(text, players, tone, show, null);
    }

    /** @param cue told when each part of the line starts playing (never called when there is no voice); may be null */
    public static void speak(String text, List<Player> players, String tone, Runnable show, VoiceCue cue) {
        if (backend == null || players.isEmpty() || "off".equals(GodSettings.ttsEngine())) {
            if (show != null) show.run();
            return;
        }
        hintUnconnected(players);
        if (!anyConnected(players)) {
            // Nobody has the voice mod: no reason to synthesize, or to keep them waiting.
            if (show != null) show.run();
            return;
        }
        List<Player> targets = new ArrayList<>(players);
        utter(text, effectNow(tone), reveal(show), cue, (pcm, onStart) -> backend.playTo(pcm, targets, onStart));
    }

    /** Speaks from a place in the world (the vessel). Any thread. */
    public static void speakAt(String text, Location at, List<Player> heard) {
        speakAt(text, at, heard, null);
    }

    public static void speakAt(String text, Location at, List<Player> heard, String tone) {
        speakAt(text, at, heard, tone, null);
    }

    /** @param show as in {@link #speak(String, List, String, Runnable)} */
    public static void speakAt(String text, Location at, List<Player> heard, String tone, Runnable show) {
        speakAt(text, at, heard, tone, show, null);
    }

    /** @param cue as in {@link #speak(String, List, String, Runnable, VoiceCue)} */
    public static void speakAt(String text, Location at, List<Player> heard, String tone, Runnable show,
            VoiceCue cue) {
        if (backend == null || "off".equals(GodSettings.ttsEngine())) {
            if (show != null) show.run();
            return;
        }
        hintUnconnected(heard);
        if (!anyConnected(heard)) {
            if (show != null) show.run();
            return;
        }
        Location where = at.clone();
        double distance = GodSettings.vesselSpeechRadius();
        utter(text, effectNow(tone), reveal(show), cue,
                (pcm, onStart) -> backend.playAt(pcm, where, distance, onStart));
    }

    private static boolean anyConnected(List<Player> players) {
        if (players == null) return false;
        for (Player p : players) {
            try {
                if (backend.connected(p)) return true;
            } catch (RuntimeException e) {
                // treat as not connected
            }
        }
        return false;
    }

    /** Shows a line's text once, on the server thread, whoever asks first (the voice starting, or the wait running out). */
    static final class Reveal {
        private final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        private final Runnable show;

        Reveal(Runnable show) {
            this.show = show;
        }

        void fire() {
            if (show == null || !done.compareAndSet(false, true)) return;
            try {
                if (Bukkit.isPrimaryThread()) show.run();
                else Bukkit.getScheduler().runTask(GPTGOD.get(), show);
            } catch (RuntimeException e) {
                // The plugin is shutting down: the words are lost with it.
            }
        }
    }

    /** Holds the text for the voice, but never longer than voice.tts.text-wait-ms. */
    private static Reveal reveal(Runnable show) {
        Reveal r = new Reveal(show);
        if (show == null) return r;
        int wait = GodSettings.ttsTextWaitMs();
        if (wait <= 0) {
            r.fire();
            return r;
        }
        try {
            Bukkit.getScheduler().runTaskLater(GPTGOD.get(), r::fire, Math.max(1, wait / 50));
        } catch (RuntimeException e) {
            r.fire();
        }
        return r;
    }

    /**
     * Synthesizes a line part by part and hands each part to playback as soon as it is ready, so the
     * first words are heard after about a second however long the line is; the rest is made while
     * they play.
     */
    private static void utter(String text, String effect, Reveal reveal, VoiceCue cue,
            java.util.function.BiConsumer<short[], Runnable> play) {
        long asked = System.currentTimeMillis();
        // Bound to the speaking god (its realm's settings).
        voice.execute(Realm.bound(() -> {
            boolean any = false;
            try {
                List<String> parts = Phrases.split(text);
                for (int i = 0; i < parts.size(); i++) {
                    short[] pcm = render(parts.get(i), effect, i == parts.size() - 1);
                    if (pcm == null) break;
                    if (!any) {
                        GPTGOD.LOGGER.info("[God voice] first words ready after " + (System.currentTimeMillis() - asked)
                                + " ms (" + text.length() + " characters in " + parts.size() + " part(s))");
                    }
                    play.accept(pcm, onStart(pcm, parts.get(i).length(), cue, any ? null : reveal));
                    any = true;
                }
            } finally {
                if (!any) reveal.fire();
            }
        }));
    }

    /** What runs when a part's turn to play comes: the cue hears of it, then the first part shows the text. */
    private static Runnable onStart(short[] pcm, int chars, VoiceCue cue, Reveal reveal) {
        if (cue == null) return reveal == null ? null : reveal::fire;
        float[] envelope = Loudness.envelope(pcm, GodVoiceFx.OUT_RATE);
        int ms = Loudness.millis(pcm, GodVoiceFx.OUT_RATE);
        return () -> {
            try {
                cue.part(envelope, ms, chars);
            } catch (RuntimeException e) {
                // the voice goes on without its subtitles
            }
            if (reveal != null) reveal.fire();
        };
    }

    /** Players already told (this server session) that they need the mod to hear the god. */
    private static final java.util.Set<java.util.UUID> HINTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The first time the god speaks to someone without voice chat, they (and the console) are told why it is silent. */
    private static void hintUnconnected(List<Player> players) {
        if (players == null) return;
        for (Player p : players) {
            boolean connected;
            try {
                connected = backend.connected(p);
            } catch (RuntimeException e) {
                continue;
            }
            if (connected || !HINTED.add(p.getUniqueId())) continue;
            GPTGOD.LOGGER.info("[God voice] " + p.getName() + " is not connected to voice chat, so the god's voice "
                    + "cannot reach them (they need the Simple Voice Chat mod on their client).");
            if (GodSettings.voiceHint()) {
                p.sendMessage(net.kyori.adventure.text.Component.text(
                        "✦ The god speaks aloud, but you cannot hear it: install the Simple Voice Chat mod "
                                + "(modrinth.com/mod/simple-voice-chat) to hear its voice and speak to it.",
                        net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true));
            }
        }
    }

    /** "2 of 3 players connected (Jumpathy, Tono)" for /god status, or why voice is off. */
    public static String voiceSummary() {
        if (backend == null) return "off (Simple Voice Chat is not installed on the server, or voice is disabled)";
        List<String> on = new ArrayList<>();
        int total = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            total++;
            try {
                if (backend.connected(p)) on.add(p.getName());
            } catch (RuntimeException e) {
                // ignore
            }
        }
        return on.size() + " of " + total + " players connected to voice chat" + (on.isEmpty() ? ""
                : " (" + String.join(", ", on) + ")") + "; speaking with " + GodSettings.ttsEngine();
    }

    /** The effect for a line spoken now: the chosen tone, else the configured one, else by mood. */
    public static String effectNow(String tone) {
        String mood = null;
        try {
            mood = God.vessel().mood().id();
        } catch (RuntimeException e) {
            // No vessel in this realm: speak as its heart feels.
        }
        if (mood == null || "calm".equals(mood)) {
            try {
                // Formless, or calm in body: the voice still carries its lasting feelings.
                mood = Realm.current().memory().heart.mood(System.currentTimeMillis()).id();
            } catch (RuntimeException e) {
                // no memory bound (tests)
            }
        }
        Persona p = Persona.current();
        return VoiceTone.choose(tone, GodSettings.voiceEffect(), mood, GodSettings.voiceMoodEffects(),
                p == null ? null : p.voiceEffect);
    }

    // ---- Getting a head start ----------------------------------------------------------------------

    private record Early(java.util.concurrent.Future<Synthesizer.Pcm> pcm, long at) {
    }

    /** Opening phrases being synthesized before their line is finished, by text and pace. */
    private static final java.util.Map<String, Early> EARLY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ExecutorService early = Executors.newSingleThreadExecutor(r -> daemon(r, "god-voice-early"));

    private static String earlyKey(String text, double pace) {
        return Math.round(pace * 100) + "|" + text;
    }

    /**
     * Called while the god is still writing a line it will speak (the tool call is streaming in):
     * starts synthesizing its opening phrase, so the voice is ready sooner when the line is spoken.
     * Harmless if the line turns out differently. Any thread; the speaking god must be bound.
     *
     * @param json the tool call's arguments so far (incomplete JSON)
     */
    /** Whether the line being written has an opening phrase that can be synthesized already. */
    public static boolean wouldPrepare(String json) {
        return backend != null && Phrases.opening(Phrases.partialField(json, "message")) != null;
    }

    public static void prepare(String json) {
        if (backend == null || "off".equals(GodSettings.ttsEngine())) return;
        try {
            String opening = Phrases.opening(Phrases.partialField(json, "message"));
            if (opening == null) return;
            boolean anyone = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (backend.connected(p)) {
                    anyone = true;
                    break;
                }
            }
            if (!anyone) return;
            Phrases.Partial tone = Phrases.partialField(json, "tone");
            String effect = effectNow(tone != null && tone.closed() ? tone.text() : null);
            double pace = GodVoiceFx.pace(GodVoiceFx.preset(effect));
            String key = earlyKey(opening, pace);
            long now = System.currentTimeMillis();
            EARLY.values().removeIf(e -> now - e.at() > 30_000);
            if (EARLY.size() >= 6 || EARLY.containsKey(key)) return;
            EARLY.put(key, new Early(early.submit(() -> synthesizer.synthesize(opening, pace)), now));
        } catch (RuntimeException e) {
            // a head start is only a convenience
        }
    }

    /** The synthesis started early for this phrase, if any (waits for it to finish). */
    private static Synthesizer.Pcm takeEarly(String text, double pace) {
        Early e = EARLY.remove(earlyKey(text, pace));
        if (e == null) return null;
        try {
            Synthesizer.Pcm pcm = e.pcm().get(20, java.util.concurrent.TimeUnit.SECONDS);
            if (pcm != null) GPTGOD.LOGGER.info("[God voice] opening phrase was prepared while the god was still writing");
            return pcm;
        } catch (Exception ex) {
            e.pcm().cancel(true);
            return null;
        }
    }

    private static short[] render(String text, String effect, boolean last) {
        try {
            GodVoiceFx.Preset preset = GodVoiceFx.preset(effect);
            Synthesizer.Pcm raw = takeEarly(text, GodVoiceFx.pace(preset));
            if (raw == null) raw = synthesizer.synthesize(text, GodVoiceFx.pace(preset));
            if (raw == null) return null;
            return GodVoiceFx.process(raw.samples(), raw.sampleRate(), preset, GodSettings.voiceVolume(), last);
        } catch (Exception e) {
            GPTGOD.LOGGER.warn("The god's voice failed: " + e.getMessage());
            return null;
        }
    }
}
