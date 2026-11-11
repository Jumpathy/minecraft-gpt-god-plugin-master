package net.bigyous.gptgodmc.loggables;

/** An instruction from the server operator, delivered with `/god say`. */
public class SystemInjectionLoggable extends BaseLoggable {
    private final String message;

    public SystemInjectionLoggable(String message) {
        this.message = message;
    }

    @Override
    public String getLog() {
        return message;
    }

    @Override
    public Category category() {
        return Category.DIRECTIVE;
    }

    @Override
    public String attentionReason() {
        return "operator directive";
    }
}
