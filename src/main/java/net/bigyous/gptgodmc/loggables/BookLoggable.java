package net.bigyous.gptgodmc.loggables;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;

import net.bigyous.gptgodmc.god.Guard;
import net.bigyous.gptgodmc.god.Writings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** A mortal wrote in a book or signed one: the god reads what it says. */
public class BookLoggable extends BaseLoggable {
    private final String playerName;
    private final String title;
    private final String text;
    private final boolean signed;
    private final Guard.Verdict verdict;

    public BookLoggable(PlayerEditBookEvent event) {
        this.playerName = event.getPlayer().getName();
        BookMeta meta = event.getNewBookMeta();
        this.signed = event.isSigning();
        this.title = signed && meta.hasTitle() ? meta.getTitle() : null;
        this.text = Writings.book(pages(meta), Writings.BOOK_CHARS);
        boolean aimed;
        try {
            aimed = ChatLoggable.addressesGod(playerName, (title == null ? "" : title + " ") + text);
        } catch (RuntimeException e) {
            aimed = false;
        }
        this.verdict = text.isBlank() ? null : Guard.note(playerName, text, aimed);
    }

    /** A book's pages as plain text. */
    public static List<String> pages(BookMeta meta) {
        List<String> out = new ArrayList<>();
        for (Component c : meta.pages()) {
            out.add(c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c));
        }
        return out;
    }

    @Override
    public String getLog() {
        if (text.isBlank()) return null;
        return String.format("%s %s: \"%s\"", playerName, signed ? "signed a book" + (title == null ? ""
                : " titled '" + Guard.clean(title) + "'") : "wrote in a book", clean(text))
                + Guard.warning(verdict);
    }

    @Override
    public Category category() {
        return Category.SPEECH;
    }

    @Override
    public String attentionReason() {
        return ChatLoggable.addressesGod(playerName, (title == null ? "" : title + " ") + text)
                ? playerName + " wrote a book" : null;
    }

    /** Guard.clean bounds one message; a book is cleaned piece by piece so all of it is made safe to quote. */
    public static String clean(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i += Guard.MAX_LENGTH - 10) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Guard.clean(text.substring(i, Math.min(text.length(), i + Guard.MAX_LENGTH - 10))));
        }
        return sb.toString();
    }
}
