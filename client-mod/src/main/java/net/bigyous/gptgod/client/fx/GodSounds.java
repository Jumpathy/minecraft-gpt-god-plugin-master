package net.bigyous.gptgod.client.fx;

import static net.bigyous.gptgod.client.fx.Synth.Voice.of;
import static net.bigyous.gptgod.client.fx.Synth.Wave.CRACKLE;
import static net.bigyous.gptgod.client.fx.Synth.Wave.NOISE;
import static net.bigyous.gptgod.client.fx.Synth.Wave.SAW;
import static net.bigyous.gptgod.client.fx.Synth.Wave.SINE;
import static net.bigyous.gptgod.client.fx.Synth.Wave.TRIANGLE;

import java.util.List;
import java.util.Map;

import net.bigyous.gptgod.client.fx.Synth.Patch;

/**
 * The sounds of the god itself: its arrival and departure, its true form, its wrath and blessings, and its
 * death (a long falling wail, then a blast in the manner of its death). Ids are gptgodmc:god.<name>.
 */
final class GodSounds {
    private GodSounds() {
    }

    static void addTo(Map<String, Patch> p) {
        String g = "gptgodmc:god.";

        // Arrival: the air draws in and rises for two and a half seconds, then the god lands in the world.
        p.put(g + "arrival", new Patch(List.of(
                of(NOISE, 0, 1, 1, 2300, 60, 500, 0.5f).lowpass(180, 7500).highpass(90),
                of(SINE, 0, 38f, 82f, 2200, 100, 300, 0.5f),
                of(SINE, 200, 293.7f, 293.7f, 1900, 300, 900, 0.14f).vibrato(5f, 0.4f),
                of(SINE, 200, 440f, 440f, 2000, 250, 900, 0.12f).vibrato(5.4f, 0.4f),
                of(SINE, 200, 587.3f, 587.3f, 2100, 200, 900, 0.11f).vibrato(4.7f, 0.4f),
                of(SINE, 2400, 110f, 42f, 4, 60, 800, 0.9f),
                of(NOISE, 2400, 1, 1, 3, 30, 700, 0.6f).lowpass(6500, 260),
                of(SINE, 2410, 1174.7f, 1174.7f, 4, 30, 1100, 0.22f),
                of(TRIANGLE, 2430, 1760f, 1760f, 4, 20, 900, 0.13f)), 140, 0.4f, 31));

        // Departure: a bell, then everything falls away downward.
        p.put(g + "departure", new Patch(List.of(
                of(SINE, 0, 1174.7f, 1174.7f, 4, 30, 900, 0.22f),
                of(SINE, 30, 587.3f, 293.7f, 80, 200, 1300, 0.16f).vibrato(5f, 0.4f),
                of(SINE, 30, 880f, 440f, 80, 150, 1300, 0.12f).vibrato(5.6f, 0.4f),
                of(NOISE, 0, 1, 1, 60, 100, 1500, 0.5f).lowpass(6500, 160).highpass(80),
                of(SINE, 100, 84f, 34f, 200, 300, 1100, 0.5f)), 150, 0.38f, 32));

        // True form: a mind-breaking cluster opening up, tones beating against each other.
        p.put(g + "trueform", new Patch(List.of(
                of(SAW, 0, 110f, 110f, 1400, 1500, 1100, 0.3f).lowpass(180, 2600).tremolo(7f, 0.35f),
                of(SAW, 0, 116.5f, 117.5f, 1500, 1400, 1100, 0.28f).lowpass(180, 2400).tremolo(8.3f, 0.35f),
                of(SAW, 200, 155.6f, 164.8f, 1500, 1300, 1000, 0.22f).lowpass(240, 3000),
                of(SINE, 300, 2093f, 2093f, 1200, 1500, 1100, 0.1f),
                of(SINE, 300, 2217.5f, 2203f, 1200, 1500, 1100, 0.1f),
                of(SINE, 0, 36.7f, 36.7f, 800, 2200, 1000, 0.6f).tremolo(3f, 0.4f),
                of(NOISE, 0, 1, 1, 2200, 600, 1200, 0.3f).lowpass(300, 9000).highpass(200),
                of(CRACKLE, 600, 8f, 60f, 400, 2000, 900, 0.3f).highpass(1500)), 170, 0.35f, 33));

        // Smite: the sky splits (a crack you feel in your teeth), three booms land one on another, and the
        // thunder rolls away for seconds.
        p.put(g + "smite", new Patch(List.of(
                of(NOISE, 0, 1, 1, 1, 25, 220, 1f).highpass(1400),
                of(SINE, 0, 2400f, 90f, 2, 25, 260, 0.6f),
                of(SINE, 10, 95f, 30f, 4, 120, 1500, 1f),
                of(SINE, 190, 78f, 27f, 6, 120, 1600, 0.9f),
                of(SINE, 430, 62f, 24f, 8, 150, 1900, 0.85f),
                of(NOISE, 20, 1, 1, 8, 200, 1700, 1f).lowpass(1500, 70),
                of(NOISE, 200, 1, 1, 150, 500, 2300, 0.9f).lowpass(260, 60).tremolo(7f, 0.55f),
                of(NOISE, 600, 1, 1, 300, 600, 2100, 0.6f).lowpass(180, 50).tremolo(4.3f, 0.6f),
                of(SAW, 10, 55f, 36.7f, 10, 200, 1400, 0.4f).lowpass(300, 90),
                of(CRACKLE, 40, 260f, 5f, 4, 250, 1200, 0.5f).highpass(700)), 240, 0.38f, 34));

        // Wound: a blade ringing off something that should not bleed, and a groan under it.
        p.put(g + "wound", new Patch(List.of(
                of(SINE, 0, 523.3f, 520f, 2, 20, 420, 0.3f),
                of(SINE, 0, 787f, 783f, 2, 15, 380, 0.26f),
                of(SINE, 0, 1245f, 1240f, 2, 10, 320, 0.2f),
                of(SINE, 0, 1661f, 1655f, 2, 10, 260, 0.16f),
                of(NOISE, 0, 1, 1, 1, 10, 90, 0.7f).highpass(2500),
                of(SAW, 40, 98f, 69.3f, 40, 120, 420, 0.4f).lowpass(500, 220).tremolo(13f, 0.4f)), 0, 0, 35));

        // The fall: five seconds of a god coming apart, sinking in pitch as its light fails.
        p.put(g + "death.fall", new Patch(List.of(
                of(SAW, 0, 440f, 55f, 300, 3900, 800, 0.32f).lowpass(2400, 260).vibrato(6f, 1f),
                of(SAW, 0, 446f, 52f, 300, 3900, 800, 0.26f).lowpass(2200, 240).vibrato(5.1f, 1f),
                of(SINE, 0, 220f, 27.5f, 400, 3800, 800, 0.5f),
                of(NOISE, 0, 1, 1, 3500, 800, 700, 0.5f).lowpass(120, 900),
                of(CRACKLE, 300, 5f, 140f, 600, 3400, 700, 0.5f).highpass(1200),
                of(SINE, 0, 1046.5f, 349.2f, 200, 2500, 2300, 0.12f).vibrato(7f, 0.8f),
                of(SINE, 0, 1568f, 466.2f, 200, 2300, 2500, 0.09f).vibrato(6.3f, 0.8f)), 210, 0.3f, 36));

        // Supernova: a blinding blast and a long ringing afterglow.
        p.put(g + "death.supernova", new Patch(List.of(
                of(NOISE, 0, 1, 1, 2, 120, 3200, 1f).lowpass(10000, 140),
                of(SINE, 0, 64f, 24f, 5, 300, 3400, 0.9f),
                of(SINE, 40, 1046.5f, 1046.5f, 30, 200, 3600, 0.16f).vibrato(5f, 0.3f),
                of(SINE, 40, 1318.5f, 1318.5f, 30, 200, 3600, 0.14f).vibrato(5.5f, 0.3f),
                of(SINE, 40, 1568f, 1568f, 30, 200, 3500, 0.13f).vibrato(4.6f, 0.3f),
                of(SINE, 60, 2093f, 2093f, 30, 150, 3300, 0.1f),
                of(NOISE, 200, 1, 1, 400, 600, 2600, 0.25f).lowpass(6000, 9000).highpass(4000),
                of(CRACKLE, 100, 200f, 4f, 10, 600, 2400, 0.4f).highpass(1000)), 230, 0.42f, 37));

        // Unraveling: the god's notes come loose one after another and drift off.
        p.put(g + "death.unraveling", new Patch(List.of(
                of(SINE, 0, 2093f, 2060f, 4, 40, 1500, 0.2f),
                of(SINE, 160, 1760f, 1730f, 4, 40, 1500, 0.2f),
                of(SINE, 320, 1396.9f, 1370f, 4, 40, 1500, 0.2f),
                of(SINE, 480, 1174.7f, 1150f, 4, 40, 1600, 0.2f),
                of(SINE, 660, 880f, 860f, 4, 40, 1700, 0.2f),
                of(SINE, 860, 698.5f, 680f, 4, 40, 1800, 0.2f),
                of(SINE, 1100, 523.3f, 500f, 4, 40, 2000, 0.22f),
                of(SINE, 1400, 392f, 196f, 4, 60, 2400, 0.24f),
                of(NOISE, 0, 1, 1, 300, 1200, 2400, 0.22f).lowpass(9000, 2500).highpass(3000),
                of(SINE, 0, 98f, 49f, 200, 1500, 2200, 0.4f)), 260, 0.45f, 38));

        // Shattering: it breaks like a cathedral window.
        p.put(g + "death.shattering", new Patch(List.of(
                of(NOISE, 0, 1, 1, 1, 40, 500, 1f).highpass(3000),
                of(CRACKLE, 0, 900f, 20f, 2, 400, 2600, 0.8f).highpass(2200),
                of(SINE, 0, 1479f, 1470f, 2, 20, 900, 0.22f),
                of(SINE, 90, 2217f, 2200f, 2, 20, 800, 0.2f),
                of(SINE, 210, 3322f, 3300f, 2, 15, 700, 0.16f),
                of(SINE, 380, 1865f, 1850f, 2, 15, 900, 0.16f),
                of(SINE, 620, 2794f, 2780f, 2, 15, 800, 0.13f),
                of(SINE, 0, 80f, 30f, 4, 150, 1800, 0.8f),
                of(NOISE, 20, 1, 1, 10, 150, 2000, 0.5f).lowpass(2000, 150)), 120, 0.36f, 39));

        // Abyssal: it is swallowed. Almost all of this is felt rather than heard.
        p.put(g + "death.abyssal", new Patch(List.of(
                of(SINE, 0, 46f, 21f, 200, 1500, 2800, 1f),
                of(SAW, 0, 55f, 30f, 300, 1400, 2600, 0.4f).lowpass(320, 70).tremolo(5f, 0.5f),
                of(SAW, 0, 58.3f, 31f, 300, 1400, 2600, 0.35f).lowpass(300, 70).tremolo(6.1f, 0.5f),
                of(NOISE, 0, 1, 1, 400, 1200, 2800, 0.6f).lowpass(450, 55),
                of(NOISE, 0, 1, 1, 20, 80, 700, 0.4f).lowpass(3000, 200),
                of(SINE, 200, 311f, 77.8f, 300, 800, 2400, 0.14f).vibrato(3f, 1f),
                of(CRACKLE, 500, 30f, 2f, 100, 1200, 2200, 0.25f).highpass(700)), 300, 0.4f, 40));

        // Blessing: a rising chime of four notes with air around them.
        p.put(g + "blessing", new Patch(List.of(
                of(SINE, 0, 523.3f, 523.3f, 4, 30, 700, 0.22f),
                of(SINE, 110, 659.3f, 659.3f, 4, 30, 700, 0.22f),
                of(SINE, 220, 784f, 784f, 4, 30, 750, 0.22f),
                of(SINE, 340, 1046.5f, 1046.5f, 4, 40, 900, 0.24f),
                of(TRIANGLE, 340, 2093f, 2093f, 4, 20, 600, 0.08f),
                of(NOISE, 0, 1, 1, 200, 300, 800, 0.16f).lowpass(5000, 9000).highpass(3500)), 130, 0.4f, 41));

        // Curse: a sour interval that sinks, with something crawling in it.
        p.put(g + "curse", new Patch(List.of(
                of(SAW, 0, 146.8f, 138.6f, 80, 500, 900, 0.3f).lowpass(700, 220).tremolo(9f, 0.5f),
                of(SAW, 0, 207.7f, 185f, 80, 500, 900, 0.26f).lowpass(700, 220).tremolo(11f, 0.5f),
                of(SINE, 0, 415f, 98f, 20, 300, 1100, 0.2f).vibrato(8f, 1f),
                of(SINE, 0, 61.7f, 41.2f, 60, 500, 900, 0.5f),
                of(CRACKLE, 100, 70f, 10f, 40, 600, 800, 0.35f).highpass(900)), 160, 0.3f, 42));

        // Meteor: the hit, the ground answering, the debris coming down.
        p.put(g + "meteor", new Patch(List.of(
                of(NOISE, 0, 1, 1, 2, 80, 2200, 1f).lowpass(5500, 90),
                of(SINE, 0, 58f, 22f, 4, 200, 2200, 1f),
                of(SAW, 0, 82.4f, 41.2f, 10, 150, 1200, 0.3f).lowpass(400, 120),
                of(CRACKLE, 150, 180f, 8f, 20, 700, 1500, 0.5f).highpass(600)), 0, 0, 43));

        // Earthquake: four seconds of the world grinding.
        p.put(g + "quake", new Patch(List.of(
                of(NOISE, 0, 1, 1, 500, 2800, 900, 1f).lowpass(95, 170).tremolo(9f, 0.45f),
                of(SINE, 0, 33f, 27f, 400, 2900, 900, 0.9f).tremolo(6f, 0.3f),
                of(NOISE, 0, 1, 1, 600, 2700, 800, 0.3f).lowpass(700, 400).highpass(200).tremolo(14f, 0.6f),
                of(CRACKLE, 200, 25f, 45f, 300, 2900, 800, 0.4f).lowpass(2500, 2500).highpass(300)), 0, 0, 44));

        // Omen: two soft bells a half-step apart, and a breath.
        p.put(g + "omen", new Patch(List.of(
                of(SINE, 0, 659.3f, 659.3f, 6, 40, 1300, 0.22f),
                of(SINE, 420, 622.3f, 622.3f, 6, 40, 1500, 0.22f),
                of(SINE, 0, 1318.5f, 1318.5f, 6, 20, 700, 0.07f),
                of(NOISE, 300, 1, 1, 400, 200, 900, 0.14f).lowpass(2500, 6000).highpass(1800)), 280, 0.45f, 45));
    }
}
