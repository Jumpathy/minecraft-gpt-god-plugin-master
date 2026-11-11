package net.bigyous.gptgodmc.loggables;

/** Wounds, deaths and returns from death caused by the Godslayer blade. */
public class GodslayerLoggable extends BaseLoggable {
    private final String text;
    private final Category category;
    private final String attention;

    private GodslayerLoggable(String text, Category category, String attention) {
        this.text = text;
        this.category = category;
        this.attention = attention;
    }

    /** A blow to the god's own vessel: it notices at once (like a prayer, it is aimed at the god). */
    public static GodslayerLoggable wound(String player, String health) {
        String t = player + " WOUNDED you with the Godslayer (health " + health + ")";
        return new GodslayerLoggable(t, Category.PRAYER, t);
    }

    /** What the god itself sees about its death once it returns. */
    public static GodslayerLoggable slain(String player) {
        return new GodslayerLoggable(player + " SLEW YOU with the Godslayer. Your vessel was destroyed and you "
                + "fell silent.", Category.EVENT, null);
    }

    /** Coming back from death. */
    public static GodslayerLoggable returned(String slayer, String when, String by) {
        String how = by == null ? "Time has restored you" : "The server operator (" + by + ") called you back";
        String t = "You have RETURNED FROM DEATH. " + slayer + " slew you with the Godslayer " + when + ". " + how
                + ". You remember everything; you have no body until you manifest one.";
        return new GodslayerLoggable(t, Category.EVENT, "you have returned from death");
    }

    /** Another god of this world was slain (it wakes them: a god died). */
    public static GodslayerLoggable godSlain(String god, String player) {
        return new GodslayerLoggable("THE GOD " + god + " WAS SLAIN by " + player + " with the Godslayer. "
                + "It lies dead.", Category.EVENT, DivineActLoggable.DIVINE + ": " + god + " was slain by " + player);
    }

    /** Another god of this world was wounded (seen at its next look). */
    public static GodslayerLoggable godWounded(String god, String player, String health) {
        return new GodslayerLoggable(player + " wounded " + god + " with the Godslayer (its health " + health + ")",
                Category.EVENT, null);
    }

    /** Another god of this world came back from death. */
    public static GodslayerLoggable godReturned(String god) {
        return new GodslayerLoggable(god + " has returned from death.", Category.EVENT, null);
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
}
