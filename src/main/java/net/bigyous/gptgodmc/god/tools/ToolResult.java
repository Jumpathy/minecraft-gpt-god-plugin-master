package net.bigyous.gptgodmc.god.tools;

/** What a tool reports back to the god. Errors are returned to Claude with is_error=true. */
public final class ToolResult {
    private final String text;
    private final boolean error;
    private final byte[] png;

    private ToolResult(String text, boolean error, byte[] png) {
        this.text = text;
        this.error = error;
        this.png = png;
    }

    public static ToolResult ok(String text) {
        return new ToolResult(text, false, null);
    }

    public static ToolResult ok(String format, Object... args) {
        return new ToolResult(String.format(format, args), false, null);
    }

    public static ToolResult error(String text) {
        return new ToolResult(text, true, null);
    }

    public static ToolResult image(String caption, byte[] png) {
        return new ToolResult(caption, false, png);
    }

    public String text() {
        return text;
    }

    public boolean isError() {
        return error;
    }

    public byte[] png() {
        return png;
    }

    public boolean hasImage() {
        return png != null && png.length > 0;
    }
}
