package net.bigyous.gptgodmc.god;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.client.ModVoice;
import net.bigyous.gptgodmc.voice.VoiceModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/** How the god's words reach mortals. All methods must run on the server thread. */
public final class Speech {
    public static final TextColor VIOLET = TextColor.color(0xC77DFF);
    public static final TextColor DEEP_VIOLET = TextColor.color(0x7B2CBF);
    public static final int MAX_LENGTH = 280;

    private Speech() {
    }

    private static String name() {
        Persona p = Persona.current();
        return p == null ? "The God" : p.shortName();
    }

    /** Puts the god's own words into the conversation timeline it sees. */
    private static void said(String line) {
        try {
            Realm.current().conversation().spoke(System.currentTimeMillis(), line);
        } catch (RuntimeException e) {
            // no realm bound (tests): nothing to record
        }
    }

    public static String clean(String message) {
        String m = message.replace('\n', ' ').trim();
        return m.length() > MAX_LENGTH ? m.substring(0, MAX_LENGTH - 1) + "…" : m;
    }

    public static void whisper(Player player, String message) {
        whisper(player, message, null);
    }

    /** @param tone the voice effect for this line (ethereal, commanding, demonic, ...), or null for the mood's */
    public static void whisper(Player player, String message, String tone) {
        String text = clean(message);
        Component line = Component.text("✦ " + name() + " whispers to you: ", DEEP_VIOLET)
                .decoration(TextDecoration.ITALIC, true)
                .append(Component.text(text, VIOLET).decoration(TextDecoration.ITALIC, false));
        // The text is shown when the voice starts (or at once when there is no voice to wait for).
        // Client-mod players also get it as a subtitle, at the same moment.
        ModVoice.Line subtitle = ModVoice.line(ModVoice.WHISPER, text, tone, List.of(player), null);
        VoiceModule.speak(text, List.of(player), tone, () -> {
            player.sendMessage(line);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f);
            subtitle.show();
        }, subtitle);
        said("You whispered to " + player.getName() + " (only they heard): \"" + text + "\"");
        Journal.add(Journal.Kind.SPEECH, "whispered to " + player.getName() + ": " + text);
    }

    public static void announce(String message, boolean asTitle) {
        announce(message, asTitle, null);
    }

    public static void announce(String message, boolean asTitle, String tone) {
        String text = clean(message);
        Component line = Component.text("✦ " + name() + " ✦ ", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(text, VIOLET).decoration(TextDecoration.BOLD, false));
        // Heard in every world this god rules (its Nether and End too), and nowhere else.
        List<Player> listeners = List.copyOf(Realm.current().players());
        Title title = !asTitle ? null
                : Title.title(Component.text(name(), DEEP_VIOLET).decoration(TextDecoration.BOLD, true),
                        Component.text(text.length() > 60 ? text.substring(0, 59) + "…" : text, VIOLET),
                        Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        ModVoice.Line subtitle = ModVoice.line(ModVoice.ANNOUNCE, text, tone, listeners, null);
        VoiceModule.speak(text, listeners, tone, () -> {
            for (Player p : listeners) {
                p.sendMessage(line);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 0.5f);
                // For client-mod players the subtitle is the proclamation; everyone else gets the title.
                if (title != null && !ModVoice.subtitled(p)) p.showTitle(title);
            }
            subtitle.show();
        }, subtitle);
        said("You announced to everyone: \"" + text + "\"");
        Journal.add(Journal.Kind.SPEECH, (asTitle ? "proclaimed to all: " : "announced to all: ") + text);
    }

    /** Spoken by the vessel: heard by mortals nearby, shown above its head. */
    public static List<Player> fromVessel(Location at, String message, int radius) {
        return fromVessel(at, message, radius, null);
    }

    public static List<Player> fromVessel(Location at, String message, int radius, String tone) {
        return fromVessel(at, message, radius, tone, null);
    }

    /** @param alsoShow run together with the text (the speech bubble over the vessel); may be null */
    public static List<Player> fromVessel(Location at, String message, int radius, String tone, Runnable alsoShow) {
        String text = clean(message);
        List<Player> heard = new ArrayList<>();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getWorld().equals(at.getWorld()) && p.getLocation().distanceSquared(at) <= (double) radius * radius) {
                heard.add(p);
            }
        }
        Component line = Component.text("[" + name() + "] ", DEEP_VIOLET).decoration(TextDecoration.BOLD, true)
                .append(Component.text(text, VIOLET).decoration(TextDecoration.BOLD, false));
        Location where = at.clone();
        ModVoice.Line subtitle = ModVoice.line(ModVoice.VESSEL, text, tone, heard, where);
        VoiceModule.speakAt(text, at, heard, tone, () -> {
            for (Player p : heard) p.sendMessage(line);
            where.getWorld().playSound(where, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.5f);
            if (alsoShow != null) alsoShow.run();
            subtitle.show();
        }, subtitle);
        said("You said aloud through your vessel: \"" + text + "\"");
        Journal.add(Journal.Kind.SPEECH, "said aloud through the vessel: " + text);
        return heard;
    }
}
