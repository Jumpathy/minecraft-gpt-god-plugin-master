package net.bigyous.gptgodmc.voice;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** What the plugin needs from Simple Voice Chat. Implemented only when that plugin is installed. */
interface VoiceBackend {
    void register(VoiceModule module);

    /**
     * 48 kHz mono, heard "inside the head" of each player. Clips play one after another.
     *
     * @param onStart run (on the playback thread) the moment this clip's turn to play comes; may be null
     */
    void playTo(short[] pcm48k, List<Player> players, Runnable onStart);

    /** 48 kHz mono, heard positionally from a point in the world. */
    void playAt(short[] pcm48k, Location at, double distance, Runnable onStart);

    /** Whether this player's client is connected to voice chat (has the mod and joined it). */
    boolean connected(Player player);

    void shutdown();
}
