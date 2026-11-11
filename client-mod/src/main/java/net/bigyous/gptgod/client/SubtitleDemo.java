package net.bigyous.gptgod.client;

import java.util.List;

import net.bigyous.gptgod.client.hud.Subtitles;
import net.bigyous.gptgod.client.net.Protocol;
import net.minecraft.client.Minecraft;

/** /godfx say &lt;kind&gt;: a sample spoken line, played locally with a made-up voice level. */
public final class SubtitleDemo {
    private SubtitleDemo() {
    }

    public static final List<String> KINDS = List.of("whisper", "announce", "vessel", "long");

    /** A speech-like loudness curve: syllables rising and falling, with a breath now and then. */
    public static float[] envelope(int ms) {
        float[] env = new float[Math.max(1, ms / 100)];
        for (int i = 0; i < env.length; i++) {
            double syllable = Math.abs(Math.sin(i * 0.95));
            double phrase = 0.55 + 0.45 * Math.sin(i * 0.21 + 0.6);
            boolean breath = i % 17 >= 15;
            env[i] = breath ? 0.04f : (float) Math.max(0, Math.min(1, 0.15 + 0.85 * syllable * phrase));
        }
        return env;
    }

    /**
     * @param ago how long ago the line started, in ms (0 = now); the screenshot test uses it to catch a
     *            line in the middle of being said
     */
    public static boolean play(String kind, long now, long ago) {
        Minecraft mc = Minecraft.getInstance();
        Protocol.Say say;
        switch (kind) {
            case "whisper" -> say = new Protocol.Say("demo", "Vel'Azhar", 0xFF5A30,
                    "I saw what you buried beneath the oak. Keep it; it is yours now.", "ethereal", "whisper", null, 4200,
                    envelope(4200));
            case "announce" -> say = new Protocol.Say("demo", "Vel'Azhar", 0xFF5A30,
                    "Mortals! The eastern hill shall bear a tower of gold before the next moon.", "booming", "announce",
                    null, 5200, envelope(5200));
            case "vessel" -> {
                // The speaker stands behind your left shoulder, so the marker and the lean show.
                double[] from = null;
                if (mc.player != null) {
                    var look = mc.player.getLookAngle();
                    from = new double[] { mc.player.getX() - look.x * 3 + look.z * 6, mc.player.getEyeY() + 1,
                            mc.player.getZ() - look.z * 3 - look.x * 6 };
                }
                say = new Protocol.Say("demo", "Vel'Azhar", 0xFF5A30, "Kneel, and I may yet be merciful.", "demonic",
                        "vessel", from, 2600, envelope(2600));
            }
            case "long" -> say = new Protocol.Say("demo", "Vel'Azhar", 0xFF5A30,
                    "You ask why the river turned to glass. Because I was tired of its noise, and because the fish "
                            + "had stopped praying. Bring me three songs before dawn and I will let it run again, "
                            + "louder than before.",
                    "majestic", "vessel", null, 13_000, envelope(13_000));
            default -> {
                return false;
            }
        }
        Subtitles.say(say, now - Math.max(0, ago));
        return true;
    }
}
