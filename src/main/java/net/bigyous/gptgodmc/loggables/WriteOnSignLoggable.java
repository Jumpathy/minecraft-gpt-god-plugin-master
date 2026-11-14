package net.bigyous.gptgodmc.loggables;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.event.block.SignChangeEvent;

import net.bigyous.gptgodmc.god.Guard;
import net.bigyous.gptgodmc.god.Writings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** A mortal wrote on a sign: the god reads it, and it wakes the god when it names it. */
public class WriteOnSignLoggable extends BaseLoggable implements UserInputLoggable {
    private final String playerName;
    private String text;
    private final String where;
    private final Guard.Verdict verdict;

    public WriteOnSignLoggable(SignChangeEvent event) {
        this.playerName = event.getPlayer().getName();
        List<String> lines = new ArrayList<>();
        for (Component c : event.lines()) {
            lines.add(c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c));
        }
        this.text = Writings.sign(lines);
        Location l = event.getBlock().getLocation();
        this.where = "(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")";
        boolean aimed;
        try {
            aimed = ChatLoggable.addressesGod(playerName, text);
        } catch (RuntimeException e) {
            aimed = false;
        }
        this.verdict = text.isBlank() ? null : Guard.note(playerName, text, aimed);
    }

    @Override
    public String getLog() {
        if (text == null || text.isBlank()) return null;
        return String.format("%s wrote on a sign at %s: \"%s\"", playerName, where, Guard.clean(text))
                + Guard.warning(verdict);
    }

    @Override
    public Category category() {
        return Category.SPEECH;
    }

    @Override
    public String attentionReason() {
        return text != null && ChatLoggable.addressesGod(playerName, text) ? playerName + " wrote a sign" : null;
    }

    @Override
    public void updateUserInput(String input) {
        this.text = input;
    }
}
