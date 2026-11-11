package net.bigyous.gptgodmc.god.tools;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Speech;

/** How the god talks to mortals. */
final class VoiceTools {
    private VoiceTools() {
    }

    private static final String TONE_HELP = "How your voice sounds for this line, if mortals hear it through voice "
            + "chat: ethereal (otherworldly, the usual), commanding (hard and close, for orders and warnings), "
            + "demonic (inhuman, for wrath), or majestic, divine, booming, abyssal, none. Default: your mood's "
            + "(wrathful is demonic, ominous commanding, otherwise ethereal).";

    private static final List<String> TONES = List.of(net.bigyous.gptgodmc.voice.GodVoiceFx.PRESETS);

    private static String tone(Args a) {
        String t = a.str("tone", null);
        return t == null || t.isBlank() ? null : t.toLowerCase(java.util.Locale.ROOT);
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("whisper",
                        "Speak privately into one mortal's mind. Only they hear it. Use for personal guidance, "
                                + "warnings, answering a prayer, or promises. Keep it short (under ~120 characters) "
                                + "and in character.",
                        Schema.object()
                                .str("player", "The mortal's name.", true)
                                .str("message", "What you say.", true)
                                .choice("tone", TONE_HELP, false, TONES),
                        a -> {
                            Player p = Targets.player(a.str("player"));
                            Speech.whisper(p, a.str("message"), tone(a));
                            return ToolResult.ok("%s heard your whisper.", p.getName());
                        }),
                GodTool.sync("announce",
                        "Your voice booms across the whole world; every mortal hears it. Use for proclamations, "
                                + "judgments everyone should witness, or new commandments. Set title=true for rare, "
                                + "momentous words that should appear in giant letters on every screen.",
                        Schema.object()
                                .str("message", "What you proclaim (under ~120 characters).", true)
                                .bool("title", "Also show it as a large on-screen title. Default false.", false)
                                .choice("tone", TONE_HELP, false, TONES),
                        a -> {
                            Speech.announce(a.str("message"), a.bool("title", false), tone(a));
                            return ToolResult.ok("All mortals heard you.");
                        }),
                GodTool.sync("vessel_say",
                        "Speak aloud through your physical vessel. Only mortals near the vessel hear it, and the "
                                + "words float above your head. Use when your vessel stands among mortals.",
                        Schema.object().str("message", "What the vessel says (under ~120 characters).", true)
                                .choice("tone", TONE_HELP, false, TONES),
                        a -> {
                            Location at = God.vessel().location();
                            if (at == null) {
                                return ToolResult.error("Your vessel is not manifested. Use vessel_go first, "
                                        + "or whisper/announce instead.");
                            }
                            var vessel = God.vessel();
                            String words = Speech.clean(a.str("message"));
                            // The bubble appears with the text, when the voice starts.
                            List<Player> heard = Speech.fromVessel(at, a.str("message"),
                                    GodSettings.vesselSpeechRadius(), tone(a), () -> vessel.showSpeech(words));
                            return ToolResult.ok(heard.isEmpty() ? "No mortal was close enough to hear it."
                                    : "Heard by: " + String.join(", ", heard.stream().map(Player::getName).toList()));
                        }));
    }
}
