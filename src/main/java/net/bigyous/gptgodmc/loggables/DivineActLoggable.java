package net.bigyous.gptgodmc.loggables;

/**
 * What another god of this world did, said, or overheard being asked of it. Seen at the god's next
 * look; it wakes the god only when `attention` is set (the other god named it, or spoke to it).
 */
public class DivineActLoggable extends BaseLoggable {
    /** Starts every attention reason this class gives, so the scheduler can tell god-only wake-ups apart. */
    public static final String DIVINE = "another god";

    private final String text;
    private final Category category;
    private final String attention;

    /**
     * @param spoken    words (shown in the conversation timeline) rather than a deed
     * @param attention why it should look soon (prefixed with {@link #DIVINE}), or null to wait
     */
    public DivineActLoggable(String text, boolean spoken, String attention) {
        this.text = text;
        this.category = spoken ? Category.SPEECH : Category.EVENT;
        this.attention = attention == null ? null : DIVINE + ": " + attention;
    }

    /** A prayer a mortal made to another god, overheard. Never wakes the god. */
    public static DivineActLoggable overheard(String player, String god, String words) {
        return new DivineActLoggable(player + " prayed to " + god + " (not to you): \"" + words + "\"", false, null);
    }

    @Override
    public String getLog() {
        return text;
    }

    @Override
    public Category category() {
        return category;
    }

    @Override
    public String attentionReason() {
        return attention;
    }

    @Override
    public boolean passive() {
        return attention == null;
    }

    @Override
    public boolean remembered() {
        return false;
    }
}
