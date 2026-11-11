package net.bigyous.gptgodmc.loggables;

public class GenericEventLoggable extends BaseLoggable {
    private final String text;
    private final String attention;

    public GenericEventLoggable(String text) {
        this(text, null);
    }

    /** @param attention non-null to wake the god soon (e.g. "a mortal arrived") */
    public GenericEventLoggable(String text, String attention) {
        this.text = text;
        this.attention = attention;
    }

    @Override
    public String getLog() {
        return text;
    }

    @Override
    public String attentionReason() {
        return attention;
    }
}
