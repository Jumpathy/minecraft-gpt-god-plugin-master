package net.bigyous.gptgodmc.god.llm;

import java.util.List;

/**
 * One thought's conversation with a language model, whoever provides it. The conversation is
 * append-only: the perception goes in, then rounds of streamed, tool-using requests, each answered
 * with the tools' results. The god's brain talks only to this; Anthropic and OpenAI-compatible
 * servers each implement it.
 */
public interface LlmChat {
    /** Adds a user turn of plain text. */
    void user(String text);

    /** Adds a user turn of text with a picture after it (a model that cannot see gets the text alone). */
    default void user(String text, byte[] png) {
        user(text);
    }

    /** True when this model can see pictures. */
    default boolean sees() {
        return true;
    }

    /** Adds the model's own turn, exactly as it answered, so the next request carries it. */
    void assistant(LlmReply reply);

    /**
     * Adds the results of the tool calls of the last reply, in order, plus optional words that
     * arrived meanwhile (null or blank for none).
     */
    void toolResults(List<ToolAnswer> answers, String extraText);

    /** Builds the next request from the conversation so far. */
    Request prepare(String effort);

    /**
     * Sends a request and streams the reply. Never throws for a failed request: the failure is in
     * {@link LlmReply#error}. Exceptions thrown by the listener end the reply as broken off.
     */
    LlmReply send(Request request, Listener listener);

    /** A request about to be sent, readable by the debug webhook and the mind viewer. */
    interface Request {
        /** The whole body as sent, as JSON. */
        String bodyJson();

        /** Beta headers sent with it (Anthropic only; empty otherwise). */
        List<String> betas();

        String system();

        String chronicle();

        /** The tool list, pretty-printed JSON. */
        String toolsJson();
    }

    /** What arrives while a reply streams. Blocks are numbered per reply. */
    interface Listener {
        /** Something arrived (used to time the first token). */
        void token();

        /** A block of prose or of thinking began. */
        void open(long block, boolean thinking);

        void delta(long block, String text);

        /** A block ended. Called for every block, including tool calls; unknown blocks are ignored. */
        void close(long block);

        /** A tool call has fully arrived: carry it out now. `json` is the raw arguments text. */
        void call(String id, String name, String json);

        /** The provider ran a web search itself. */
        void search(String query);
    }
}
