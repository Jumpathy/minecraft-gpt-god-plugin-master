package net.bigyous.gptgodmc.god.llm;

import java.util.function.BiConsumer;

/**
 * Splits streamed text into thinking and prose for models that write their reasoning inline between
 * {@code <think>} and {@code </think>}. Tags may be cut anywhere between chunks.
 */
public final class ThinkSplitter {
    private static final String OPEN = "<think>", CLOSE = "</think>";
    private final StringBuilder held = new StringBuilder();
    private boolean thinking;

    /** Feeds a chunk; `out` receives (thinking?, text) pieces in order. */
    public void feed(String chunk, BiConsumer<Boolean, String> out) {
        held.append(chunk);
        while (true) {
            String tag = thinking ? CLOSE : OPEN;
            int at = held.indexOf(tag);
            if (at >= 0) {
                emit(out, held.substring(0, at));
                held.delete(0, at + tag.length());
                thinking = !thinking;
                continue;
            }
            // Keep back an ending that may be the start of the tag.
            int keep = 0;
            for (int n = Math.min(tag.length() - 1, held.length()); n > 0; n--) {
                if (tag.startsWith(held.substring(held.length() - n))) {
                    keep = n;
                    break;
                }
            }
            emit(out, held.substring(0, held.length() - keep));
            held.delete(0, held.length() - keep);
            return;
        }
    }

    /** The stream ended: whatever was held back is ordinary text after all. */
    public void end(BiConsumer<Boolean, String> out) {
        emit(out, held.toString());
        held.setLength(0);
    }

    private void emit(BiConsumer<Boolean, String> out, String text) {
        if (!text.isEmpty()) out.accept(thinking, text);
    }
}
