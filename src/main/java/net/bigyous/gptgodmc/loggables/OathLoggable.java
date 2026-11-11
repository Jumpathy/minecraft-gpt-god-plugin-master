package net.bigyous.gptgodmc.loggables;

import net.bigyous.gptgodmc.god.Guard;

/** A mortal swore an oath before the god (/swear); it waits for the god's answer. */
public class OathLoggable extends BaseLoggable {
    private final String text;
    private final Guard.Verdict verdict;

    public OathLoggable(String player, String id, String words) {
        this.text = String.format("%s swears an oath before you (%s, waiting for witness_oath): \"%s\"", player, id,
                Guard.clean(words));
        this.verdict = Guard.note(player, words, true);
    }

    @Override
    public String getLog() {
        return text + Guard.warning(verdict);
    }

    @Override
    public Category category() {
        return Category.PRAYER;
    }

    @Override
    public String attentionReason() {
        return text;
    }
}
