package net.bigyous.gptgodmc.loggables;

import java.time.Instant;
import java.util.Locale;

import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Persona;

/** A line of chat, or speech transcribed from voice chat. */
public class ChatLoggable extends BaseLoggable implements UserInputLoggable {
    public final String playerName;
    public String message;
    private final boolean spokenAloud;

    public ChatLoggable(String playerName, String message) {
        this(playerName, message, Instant.now(), false);
    }

    public ChatLoggable(String playerName, String message, Instant timestamp, boolean spokenAloud) {
        super(timestamp);
        this.playerName = playerName;
        this.message = message;
        this.spokenAloud = spokenAloud;
        boolean aimed;
        try {
            aimed = addressesGod(playerName, message);
        } catch (RuntimeException e) {
            aimed = false;
        }
        this.verdict = net.bigyous.gptgodmc.god.Guard.note(playerName, message, aimed);
    }

    /** What the god's guard makes of these words (null when the guard is off). */
    private final net.bigyous.gptgodmc.god.Guard.Verdict verdict;

    @Override
    public String getLog() {
        if (message == null || message.isBlank()) {
            return null;
        }
        return String.format("%s %s: \"%s\"", playerName, spokenAloud ? "said aloud (voice)" : "said in chat",
                net.bigyous.gptgodmc.god.Guard.clean(message)) + net.bigyous.gptgodmc.god.Guard.warning(verdict);
    }

    @Override
    public Category category() {
        return Category.SPEECH;
    }

    @Override
    public String attentionReason() {
        if (addressesGod(playerName, message)) {
            return playerName + " spoke";
        }
        return null;
    }

    /**
     * True if these words should make the bound god answer: they name it, or (when no other god of its
     * world is named and it is the one this mortal's words go to) they use a general attention word.
     */
    public static boolean addressesGod(String player, String message) {
        if (message == null) return false;
        boolean general = GodSettings.wakeOnAnyChat() || mentionsWord(message);
        try {
            return net.bigyous.gptgodmc.god.Addressing.answers(player, message, general);
        } catch (RuntimeException e) {
            return general || mentionsGod(message); // no realm (tests)
        }
    }

    private static boolean mentionsWord(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (String word : GodSettings.attentionWords()) {
            if (!word.isBlank() && lower.matches("(?s).*\\b" + java.util.regex.Pattern.quote(word.toLowerCase(Locale.ROOT)) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    /** True if the message seems addressed to the god (its name or a configured attention word). */
    public static boolean mentionsGod(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        String name = Persona.current() != null ? Persona.current().shortName() : "";
        // On word boundaries, so one god's name inside another's ("Vor" in "Vorthak") does not count.
        if (!name.isBlank() && net.bigyous.gptgodmc.god.Pantheon.names(lower, name)) {
            return true;
        }
        for (String word : GodSettings.attentionWords()) {
            if (!word.isBlank() && lower.matches("(?s).*\\b" + java.util.regex.Pattern.quote(word.toLowerCase(Locale.ROOT)) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateUserInput(String input) {
        this.message = input;
    }
}
