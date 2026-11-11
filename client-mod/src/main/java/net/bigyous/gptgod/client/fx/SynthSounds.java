package net.bigyous.gptgod.client.fx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.AudioFormat;

import org.lwjgl.BufferUtils;

import net.bigyous.gptgod.client.GodClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Plays the mod's synthesized sounds in the world. The game is given a streaming sound whose audio we
 * supply ourselves (the gptgod:synth event in sounds.json only gives it a place in the sound system).
 */
public final class SynthSounds {
    private SynthSounds() {
    }

    static final Identifier EVENT = GodClientMod.id("synth");
    private static final AudioFormat FORMAT = new AudioFormat(Synth.RATE, 16, 1, true, false);

    /** Plays the sound with this id at a place. Returns false for an unknown id. */
    public static boolean play(String id, double x, double y, double z, float volume, float pitch) {
        short[] audio = SoundBank.audio(id);
        if (audio == null) return false;
        Minecraft.getInstance().getSoundManager().play(new Instance(audio, x, y, z, volume, pitch));
        return true;
    }

    private static final java.util.Map<Synth.Patch, short[]> DESIGNED = java.util.Collections
            .synchronizedMap(new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Synth.Patch, short[]> eldest) {
                    return size() > 24;
                }
            });

    /** A designed sound's audio, kept for the next time the same patch plays (loops, replays). */
    public static short[] render(Synth.Patch patch) {
        return DESIGNED.computeIfAbsent(patch, Synth::render);
    }

    /** Plays freshly rendered audio (a sound the god designed). */
    public static void play(short[] audio, double x, double y, double z, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new Instance(audio, x, y, z, volume, pitch));
    }

    private static final class Instance extends AbstractSoundInstance {
        private final short[] audio;

        Instance(short[] audio, double x, double y, double z, float volume, float pitch) {
            super(EVENT, SoundSource.AMBIENT, RandomSource.createThreadLocalInstance());
            this.audio = audio;
            this.x = x;
            this.y = y;
            this.z = z;
            this.volume = Math.max(0, Math.min(4, volume));
            this.pitch = Math.max(0.5f, Math.min(2, pitch));
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
            return CompletableFuture.completedFuture(new Stream(audio));
        }
    }

    private static final class Stream implements AudioStream {
        private final short[] audio;
        private int at;

        Stream(short[] audio) {
            this.audio = audio;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int expectedSize) {
            if (at >= audio.length) return null;
            int n = Math.min(audio.length - at, Math.max(1, expectedSize / 2));
            ByteBuffer out = BufferUtils.createByteBuffer(n * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < n; i++) out.putShort(audio[at + i]);
            at += n;
            out.flip();
            return out;
        }

        @Override
        public void close() {
        }
    }
}
