package net.bigyous.gptgod.client.fx;

import static net.bigyous.gptgod.client.fx.Synth.Voice.of;
import static net.bigyous.gptgod.client.fx.Synth.Wave.CRACKLE;
import static net.bigyous.gptgod.client.fx.Synth.Wave.NOISE;
import static net.bigyous.gptgod.client.fx.Synth.Wave.SAW;
import static net.bigyous.gptgod.client.fx.Synth.Wave.SINE;
import static net.bigyous.gptgod.client.fx.Synth.Wave.TRIANGLE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.bigyous.gptgod.client.fx.Synth.Patch;

/**
 * The mod's own sounds, by the id the plugin sends. Each is a synth patch, rendered once and kept.
 * Pure. The wings of the god's body: angel = airy, bright, a choral shimmer and big feathery whooshes;
 * demon = leathery, heavy, low, with ember crackle.
 */
public final class SoundBank {
    private SoundBank() {
    }

    private static final Map<String, Patch> PATCHES = new LinkedHashMap<>();
    private static final Map<String, short[]> RENDERED = new ConcurrentHashMap<>();

    static {
        GodSounds.addTo(PATCHES);
        String a = "gptgodmc:vessel.wings.";
        // ---- Angel ---------------------------------------------------------------------------------
        PATCHES.put(a + "appear.angel", new Patch(List.of(
                of(NOISE, 0, 1, 1, 90, 40, 230, 0.5f).lowpass(900, 5200).highpass(350),
                of(SINE, 30, 1318.5f, 1318.5f, 6, 30, 320, 0.22f),
                of(SINE, 70, 1975.5f, 1975.5f, 6, 20, 280, 0.16f),
                of(TRIANGLE, 0, 659.3f, 784f, 60, 60, 220, 0.12f).vibrato(5.5f, 0.3f)), 90, 0.3f, 11));
        PATCHES.put(a + "unfurl.angel", new Patch(List.of(
                // The great feathered sweep: air opening up, then a second, higher rush of feather tips.
                of(NOISE, 0, 1, 1, 260, 140, 470, 0.7f).lowpass(380, 6500).highpass(180),
                of(NOISE, 130, 1, 1, 160, 80, 420, 0.3f).lowpass(2500, 9000).highpass(1800),
                // A choir-like chord swelling under it.
                of(SINE, 60, 523.3f, 523.3f, 220, 180, 520, 0.16f).vibrato(5.2f, 0.35f),
                of(SINE, 60, 659.3f, 659.3f, 240, 170, 520, 0.14f).vibrato(4.8f, 0.35f),
                of(SINE, 60, 784f, 784f, 260, 160, 520, 0.14f).vibrato(5.6f, 0.35f),
                of(TRIANGLE, 80, 1046.5f, 1046.5f, 280, 140, 500, 0.08f).vibrato(6f, 0.3f),
                // Sparks of light at the top of the sweep.
                of(SINE, 330, 2637f, 3136f, 4, 10, 260, 0.14f),
                of(SINE, 450, 3136f, 3520f, 4, 10, 240, 0.11f)), 120, 0.32f, 12));
        PATCHES.put(a + "furl.angel", new Patch(List.of(
                of(NOISE, 0, 1, 1, 50, 60, 430, 0.6f).lowpass(5200, 480).highpass(160),
                of(SINE, 20, 784f, 523.3f, 40, 80, 380, 0.14f).vibrato(5f, 0.25f),
                of(SINE, 20, 1046.5f, 659.3f, 40, 60, 360, 0.09f)), 80, 0.2f, 13));
        PATCHES.put(a + "vanish.angel", new Patch(List.of(
                of(NOISE, 0, 1, 1, 30, 40, 380, 0.3f).lowpass(3000, 9000).highpass(2200),
                of(SINE, 0, 1046.5f, 2093f, 20, 60, 420, 0.2f).vibrato(6f, 0.3f),
                of(SINE, 40, 1568f, 3136f, 20, 40, 400, 0.13f),
                of(TRIANGLE, 0, 523.3f, 392f, 30, 40, 300, 0.1f)), 110, 0.38f, 14));
        PATCHES.put(a + "beat.angel", new Patch(List.of(
                of(NOISE, 0, 1, 1, 70, 20, 230, 0.7f).lowpass(520, 2900).highpass(140),
                of(NOISE, 50, 1, 1, 40, 10, 170, 0.22f).lowpass(3000, 6500).highpass(2000),
                of(SINE, 0, 120f, 72f, 30, 20, 160, 0.25f)), 0, 0, 15));
        // ---- Demon ---------------------------------------------------------------------------------
        PATCHES.put(a + "appear.demon", new Patch(List.of(
                of(NOISE, 0, 1, 1, 3, 20, 200, 0.8f).lowpass(3200, 280),
                of(SAW, 0, 73.4f, 55f, 20, 80, 240, 0.4f).lowpass(420, 260).tremolo(19f, 0.5f),
                of(CRACKLE, 40, 60f, 14f, 10, 80, 260, 0.45f).highpass(900)), 0, 0, 21));
        PATCHES.put(a + "unfurl.demon", new Patch(List.of(
                // Heavy leather dragged open.
                of(NOISE, 0, 1, 1, 220, 150, 520, 0.8f).lowpass(230, 1900).highpass(60),
                of(NOISE, 180, 1, 1, 30, 30, 260, 0.35f).lowpass(2600, 500),
                // A growl in the chest and a weight under the floor.
                of(SAW, 0, 82.4f, 61.7f, 180, 220, 480, 0.4f).lowpass(340, 520).tremolo(17f, 0.55f),
                of(SINE, 0, 55f, 41.2f, 150, 260, 480, 0.55f),
                of(SAW, 240, 123.5f, 110f, 120, 120, 380, 0.14f).lowpass(700, 400).vibrato(7f, 0.6f),
                // Embers shaken loose.
                of(CRACKLE, 120, 20f, 90f, 60, 340, 420, 0.5f).highpass(1100)), 70, 0.22f, 22));
        PATCHES.put(a + "furl.demon", new Patch(List.of(
                of(NOISE, 0, 1, 1, 40, 80, 400, 0.75f).lowpass(1800, 200).highpass(50),
                of(SAW, 0, 98f, 65.4f, 40, 120, 340, 0.3f).lowpass(420, 240).tremolo(15f, 0.5f),
                of(SINE, 340, 92f, 44f, 6, 30, 200, 0.6f),
                of(CRACKLE, 0, 70f, 10f, 20, 160, 300, 0.35f).highpass(1000)), 0, 0, 23));
        PATCHES.put(a + "vanish.demon", new Patch(List.of(
                of(SAW, 0, 110f, 38.9f, 20, 120, 460, 0.42f).lowpass(900, 160).tremolo(21f, 0.5f),
                of(NOISE, 0, 1, 1, 20, 60, 440, 0.5f).lowpass(1700, 140),
                of(SINE, 0, 61.7f, 36.7f, 30, 160, 420, 0.5f),
                of(CRACKLE, 0, 110f, 6f, 10, 200, 380, 0.5f).highpass(1200)), 95, 0.3f, 24));
        PATCHES.put(a + "beat.demon", new Patch(List.of(
                of(NOISE, 0, 1, 1, 45, 30, 270, 0.85f).lowpass(260, 1400).highpass(50),
                of(SINE, 10, 84f, 44f, 20, 30, 220, 0.6f),
                of(SAW, 0, 65.4f, 55f, 30, 40, 200, 0.2f).lowpass(300, 220),
                of(CRACKLE, 60, 40f, 8f, 10, 60, 200, 0.25f).highpass(1200)), 0, 0, 25));
    }

    public static boolean has(String id) {
        return PATCHES.containsKey(id);
    }

    public static java.util.Set<String> ids() {
        return PATCHES.keySet();
    }

    public static Patch patch(String id) {
        return PATCHES.get(id);
    }

    /** The sound's audio (mono, 16-bit, {@link Synth#RATE}), or null for an unknown id. */
    public static short[] audio(String id) {
        Patch p = PATCHES.get(id);
        return p == null ? null : RENDERED.computeIfAbsent(id, k -> Synth.render(p));
    }
}
