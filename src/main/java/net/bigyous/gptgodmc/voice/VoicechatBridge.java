package net.bigyous.gptgodmc.voice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.bigyous.gptgodmc.GPTGOD;

/** The Simple Voice Chat side: captures speech and plays the god's voice. */
final class VoicechatBridge implements VoiceBackend, VoicechatPlugin {
    private static final String CATEGORY = "god_voice";
    private static final int MAX_SAMPLES = 48_000 * 30;

    private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private final Map<UUID, Utterance> speaking = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> needsReset = ConcurrentHashMap.newKeySet();
    private final Map<UUID, StaticAudioChannel> channels = new ConcurrentHashMap<>();
    private final ExecutorService playback = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "god-voice-playback");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "god-ears-sweeper");
        t.setDaemon(true);
        return t;
    });
    private VoiceModule module;
    private volatile VoicechatServerApi api;

    private static final class Utterance {
        final Player player;
        final Instant startedAt = Instant.now();
        short[] samples = new short[0];
        long lastPacket = System.currentTimeMillis();

        Utterance(Player player) {
            this.player = player;
        }

        synchronized void add(short[] more) {
            if (samples.length + more.length > MAX_SAMPLES) return;
            short[] joined = new short[samples.length + more.length];
            System.arraycopy(samples, 0, joined, 0, samples.length);
            System.arraycopy(more, 0, joined, samples.length, more.length);
            samples = joined;
            lastPacket = System.currentTimeMillis();
        }
    }

    @Override
    public void register(VoiceModule module) {
        this.module = module;
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            throw new IllegalStateException("Simple Voice Chat service not found");
        }
        service.registerPlugin(this);
        // People sometimes stop talking without an end-of-speech packet; flush after a pause.
        sweeper.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Utterance> e : speaking.entrySet()) {
                if (now - e.getValue().lastPacket > 1200) {
                    finish(e.getKey());
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public String getPluginId() {
        return GPTGOD.PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi server) {
            this.api = server;
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onStarted);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMic);
        registration.registerEvent(PlayerDisconnectedEvent.class, e -> cleanup(e.getPlayerUuid()));
    }

    private void onStarted(VoicechatServerStartedEvent event) {
        api = event.getVoicechat();
        try {
            VolumeCategory category = api.volumeCategoryBuilder().setId(CATEGORY).setName("God")
                    .setDescription("The voice of the god").build();
            api.registerVolumeCategory(category);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.debug("Could not register the god's volume category", e);
        }
    }

    private void onMic(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || !(sender.getPlayer().getPlayer() instanceof Player player)) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        byte[] opus = event.getPacket().getOpusEncodedData();
        UUID id = player.getUniqueId();
        if (opus.length == 0) {
            finish(id);
            return;
        }
        OpusDecoder decoder = decoders.computeIfAbsent(id, k -> event.getVoicechat().createDecoder());
        short[] pcm;
        // The native decoder is only touched under its own lock, and never after close().
        synchronized (decoder) {
            if (decoder.isClosed()) return;
            if (needsReset.remove(id)) decoder.resetState();
            pcm = decoder.decode(opus);
        }
        speaking.computeIfAbsent(id, k -> new Utterance(player)).add(pcm);
    }

    /** End of an utterance. The decoder is reset lazily on the next packet, on the voice thread. */
    private void finish(UUID id) {
        Utterance u = speaking.remove(id);
        needsReset.add(id);
        if (u != null && u.samples.length > 0) {
            module.heard(u.player, u.samples, u.startedAt);
        }
    }

    private void cleanup(UUID id) {
        speaking.remove(id);
        needsReset.remove(id);
        OpusDecoder d = decoders.remove(id);
        if (d != null) {
            synchronized (d) {
                if (!d.isClosed()) d.close();
            }
        }
        channels.remove(id);
    }

    @Override
    public boolean connected(Player player) {
        VoicechatServerApi a = api;
        return a != null && a.getConnectionOf(player.getUniqueId()) != null;
    }

    @Override
    public void playTo(short[] pcm48k, List<Player> players, Runnable onStart) {
        VoicechatServerApi a = api;
        if (a == null) {
            if (onStart != null) onStart.run();
            return;
        }
        playback.execute(() -> {
            if (onStart != null) onStart.run();
            List<AudioPlayer> started = new ArrayList<>();
            for (Player p : players) {
                VoicechatConnection conn = a.getConnectionOf(p.getUniqueId());
                if (conn == null) continue;
                StaticAudioChannel ch = channels.compute(p.getUniqueId(), (k, existing) -> existing == null
                        || existing.isClosed() ? a.createStaticAudioChannel(UUID.randomUUID(),
                                a.fromServerLevel(p.getWorld()), conn) : existing);
                if (ch == null) continue;
                ch.setCategory(CATEGORY);
                AudioPlayer player = a.createAudioPlayer(ch, a.createEncoder(), pcm48k);
                player.startPlaying();
                started.add(player);
            }
            waitFor(started);
        });
    }

    @Override
    public void playAt(short[] pcm48k, Location at, double distance, Runnable onStart) {
        VoicechatServerApi a = api;
        if (a == null) {
            if (onStart != null) onStart.run();
            return;
        }
        playback.execute(() -> {
            if (onStart != null) onStart.run();
            LocationalAudioChannel ch = a.createLocationalAudioChannel(UUID.randomUUID(),
                    a.fromServerLevel(at.getWorld()), a.createPosition(at.getX(), at.getY() + 2, at.getZ()));
            if (ch == null) return;
            ch.setCategory(CATEGORY);
            ch.setDistance((float) distance);
            AudioPlayer player = a.createAudioPlayer(ch, a.createEncoder(), pcm48k);
            player.startPlaying();
            waitFor(List.of(player));
        });
    }

    /** Lines play one after another instead of over each other. */
    private static void waitFor(List<AudioPlayer> players) {
        long deadline = System.currentTimeMillis() + 60_000;
        for (AudioPlayer p : players) {
            while (p.isPlaying() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(4); // the next part starts within a few ms of this one ending
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public void shutdown() {
        sweeper.shutdownNow();
        playback.shutdownNow();
        for (OpusDecoder d : decoders.values()) {
            synchronized (d) {
                if (!d.isClosed()) d.close();
            }
        }
        decoders.clear();
        VoicechatServerApi a = api;
        if (a != null) {
            try {
                a.unregisterVolumeCategory(CATEGORY);
            } catch (RuntimeException ignored) {
                // server already gone
            }
        }
    }
}
