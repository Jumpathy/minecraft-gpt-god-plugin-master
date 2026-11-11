package net.bigyous.gptgodmc.god.tools;

import java.util.Arrays;
import java.util.List;

import net.bigyous.gptgodmc.god.Heart;
import net.bigyous.gptgodmc.god.Memory;

/** The god's own say over its heart: what moves it, and how much, is its choice. */
final class HeartTools {
    private HeartTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("feel",
                "Take a feeling to heart by your own choice, or let one go. Your heart already keeps what mortals do "
                        + "to you (insults, praise, gifts, deaths); this is for anything else, when it truly moves "
                        + "you: the sky clearing, a storm, the moon, land that thrives or is stripped, a mortal's "
                        + "deed, a memory, your own thoughts. Nothing has to move you, and most moments pass without "
                        + "it. Silent: no mortal sees it, though a stirring or strong feeling shows in your body "
                        + "and voice until it fades.",
                Schema.object()
                        .choice("feeling", "Which feeling.", true,
                                Arrays.stream(Heart.Feeling.values()).map(Heart.Feeling::id).toList())
                        .choice("strength", "faint: yours alone. stirring: your body and voice show it. strong: it "
                                + "grips you. gone: you let this feeling go.", true, Heart.STRENGTHS)
                        .str("why", "What moved you, in a few words. You will see it in your heart later.", true),
                a -> {
                    Heart.Feeling f = Heart.parse(a.str("feeling"));
                    if (f == null) throw new ToolException("Unknown feeling '" + a.str("feeling") + "'.");
                    String strength = a.str("strength");
                    if (strength == null || !Heart.STRENGTHS.contains(strength)) {
                        throw new ToolException("strength is one of " + String.join(", ", Heart.STRENGTHS) + ".");
                    }
                    Heart heart = Memory.get().heart;
                    long now = System.currentTimeMillis();
                    double level = heart.choose(f, strength, a.str("why"), now);
                    if (strength.equals("gone")) return ToolResult.ok("You let your %s go.", f.id());
                    return ToolResult.ok("Your heart holds %s %s (%d).", Heart.wordFor(level), f.id(), Math.round(level));
                }));
    }
}
